package org.apache.flink.api.java.typeutils.runtime;

import org.apache.fory.BaseFory;
import org.apache.fory.Fory;
import org.apache.fory.config.Language;
import org.apache.flink.api.common.typeutils.TypeSerializer;
import org.apache.flink.api.common.typeutils.TypeSerializerSchemaCompatibility;
import org.apache.flink.api.common.typeutils.TypeSerializerSnapshot;
import org.apache.flink.core.memory.DataInputView;
import org.apache.flink.core.memory.DataOutputView;
import org.apache.flink.api.common.typeutils.base.TypeSerializerSingleton;

import java.io.IOException;

/**
 * 为泛型类型创建一个基于 Apache Fory 的 Flink 序列化器
 */
public final class ForySerializer<T> extends TypeSerializerSingleton<T> {

    private static final long serialVersionUID = 3L; // 版本号更新

    private final Class<T> typeClass;
    // 使用 ThreadLocal 保证线程安全
    private transient ThreadLocal<BaseFory> foryThreadLocal;

    public ForySerializer(Class<T> clazz) {
        this.typeClass = clazz;
        // 延迟初始化 ThreadLocal，减少内存占用
    }

    // 创建 Fory 实例的私有辅助方法
    private BaseFory createForyInstance() {
        return Fory.builder()
                .withLanguage(Language.JAVA)
                .requireClassRegistration(false)
                .build();
    }

    // 获取当前线程的 Fory 实例（线程安全）
    private BaseFory getForyInstance() {
        // 双重检查锁定模式初始化 ThreadLocal
        ThreadLocal<BaseFory> localRef = foryThreadLocal;
        if (localRef == null) {
            synchronized (this) {
                localRef = foryThreadLocal;
                if (localRef == null) {
                    foryThreadLocal = ThreadLocal.withInitial(this::createForyInstance);
                    localRef = foryThreadLocal;
                }
            }
        }
        return localRef.get();
    }

    @Override
    public boolean isImmutableType() {
        return false;
    }

    @Override
    public ForySerializer<T> duplicate() {
        return new ForySerializer<>(typeClass);
    }

    @Override
    public T createInstance() {
        return null;
    }

    @Override
    public T copy(T from) {
        if (from == null) {
            return null;
        }
        BaseFory fory = getForyInstance();
        byte[] bytes = fory.serialize(from);
        return (T) fory.deserialize(bytes);
    }

    @Override
    public T copy(T from, T reuse) {
        return copy(from);
    }

    @Override
    public int getLength() {
        return -1; // 变长
    }

    @Override
    public void serialize(T record, DataOutputView target) throws IOException {
        if (record == null) {
            target.writeBoolean(true);
            return;
        }
        target.writeBoolean(false);

        BaseFory fory = getForyInstance();
        byte[] bytes = fory.serialize(record);
        target.writeInt(bytes.length);
        target.write(bytes);
    }

    @Override
    public T deserialize(DataInputView source) throws IOException {
        boolean isNull = source.readBoolean();
        if (isNull) {
            return null;
        }
        int length = source.readInt();
        byte[] bytes = new byte[length];
        source.readFully(bytes);

        BaseFory fory = getForyInstance();
        return (T) fory.deserialize(bytes);
    }

    @Override
    public T deserialize(T reuse, DataInputView source) throws IOException {
        return deserialize(source);
    }

    @Override
    public void copy(DataInputView source, DataOutputView target) throws IOException {
        boolean isNull = source.readBoolean();
        target.writeBoolean(isNull);
        if (!isNull) {
            int length = source.readInt();
            target.writeInt(length);
            target.write(source, length);
        }
    }

    @Override
    public boolean equals(Object obj) {
        if (obj == this) {
            return true;
        }
        if (obj instanceof ForySerializer) {
            ForySerializer<?> other = (ForySerializer<?>) obj;
            return typeClass.equals(other.typeClass);
        }
        return false;
    }

    @Override
    public int hashCode() {
        return typeClass.hashCode() ^ ForySerializer.class.hashCode();
    }

    @Override
    public TypeSerializerSnapshot<T> snapshotConfiguration() {
        return new ForySerializerSnapshot<>(this);
    }

    // --- 自定义序列化/反序列化 ---
    /**
     * 自定义 writeObject 方法，仅序列化 typeClass
     */
    private void writeObject(java.io.ObjectOutputStream out) throws IOException {
        out.defaultWriteObject(); // 序列化 typeClass
        // ThreadLocal 不会被序列化（transient）
    }

    /**
     * 自定义 readObject 方法，反序列化 typeClass
     */
    private void readObject(java.io.ObjectInputStream in) throws IOException, ClassNotFoundException {
        in.defaultReadObject(); // 反序列化 typeClass
        // ThreadLocal 会在第一次使用时懒加载初始化
    }

    public static final class ForySerializerSnapshot<T> implements TypeSerializerSnapshot<T> {
        private Class<T> typeClass;

        @SuppressWarnings("unused")
        public ForySerializerSnapshot() {}

        public ForySerializerSnapshot(ForySerializer<T> serializer) {
            this.typeClass = serializer.typeClass;
        }

        @Override
        public int getCurrentVersion() {
            return 1;
        }

        @Override
        public void writeSnapshot(DataOutputView out) throws IOException {
            out.writeUTF(typeClass.getName());
        }

        @Override
        public void readSnapshot(int readVersion, DataInputView in, ClassLoader userCodeClassLoader) throws IOException {
            String className = in.readUTF();
            try {
                this.typeClass = (Class<T>) userCodeClassLoader.loadClass(className);
            } catch (ClassNotFoundException e) {
                throw new IOException("Could not find data type class.", e);
            }
        }

        @Override
        public TypeSerializer<T> restoreSerializer() {
            return new ForySerializer<>(typeClass);
        }

        @Override
        public TypeSerializerSchemaCompatibility resolveSchemaCompatibility(TypeSerializer<T> newSerializer) {
            if (!(newSerializer instanceof ForySerializer)) {
                return TypeSerializerSchemaCompatibility.incompatible();
            }

            ForySerializer<?> other = (ForySerializer<?>) newSerializer;
            return typeClass.equals(other.typeClass)
                    ? TypeSerializerSchemaCompatibility.compatibleAsIs()
                    : TypeSerializerSchemaCompatibility.incompatible();
        }
    }
}
