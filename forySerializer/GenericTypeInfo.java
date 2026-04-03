/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2025-2025. All rights reserved.
 *
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.apache.flink.api.java.typeutils;

import org.apache.flink.annotation.Public;
import org.apache.flink.annotation.PublicEvolving;
import org.apache.flink.api.common.ExecutionConfig;
import org.apache.flink.api.common.typeinfo.AtomicType;
import org.apache.flink.api.common.typeinfo.TypeInformation;
import org.apache.flink.api.common.typeutils.TypeComparator;
import org.apache.flink.api.common.typeutils.TypeSerializer;
import org.apache.flink.api.java.typeutils.runtime.ForySerializer;
import org.apache.flink.api.java.typeutils.runtime.GenericTypeComparator;
import org.apache.flink.api.java.typeutils.runtime.kryo.KryoSerializer;
import org.apache.flink.configuration.Configuration;
import org.apache.flink.configuration.GlobalConfiguration;
import org.apache.flink.configuration.OmniSerializerOptions;

import static org.apache.flink.util.Preconditions.checkNotNull;

@Public
public class GenericTypeInfo<T> extends TypeInformation<T> implements AtomicType<T> {

    private static final long serialVersionUID = -7959114120287706504L;

    private final Class<T> typeClass;

    private static boolean OMNI_SERIALIZER_ENABLED = false;

    @PublicEvolving
    public GenericTypeInfo(Class<T> typeClass) {
        this.typeClass = checkNotNull(typeClass);
        try {
            Configuration conf = GlobalConfiguration.loadConfiguration();
            this.OMNI_SERIALIZER_ENABLED = conf.getBoolean(OmniSerializerOptions.OMNI_SERIALIZER_ENABLE);
        } catch (Exception e) {
            this.OMNI_SERIALIZER_ENABLED = false;
        }
    }

    @Override
    @PublicEvolving
    public boolean isBasicType() {
        return false;
    }

    @Override
    @PublicEvolving
    public boolean isTupleType() {
        return false;
    }

    @Override
    @PublicEvolving
    public int getArity() {
        return 1;
    }

    @Override
    @PublicEvolving
    public int getTotalFields() {
        return 1;
    }

    @Override
    @PublicEvolving
    public Class<T> getTypeClass() {
        return typeClass;
    }

    @Override
    @PublicEvolving
    public boolean isKeyType() {
        return Comparable.class.isAssignableFrom(typeClass);
    }

    @Override
    @PublicEvolving
    public TypeSerializer<T> createSerializer(ExecutionConfig config) {
        System.out.println("GenericTypeInfo.createSerializer() called for typeClass: " + typeClass);
        if (config.hasGenericTypesDisabled()) {
            throw new UnsupportedOperationException(
                    "Generic types have been disabled in the ExecutionConfig and type "
                            + this.typeClass.getName()
                            + " is treated as a generic type.");
        }
        if (typeClass == null) {
            System.out.println("ERROR: typeClass is NULL! This will cause issues in the serializer.");
            new Exception("Stack trace for null typeClass").printStackTrace();
        }

        if (OMNI_SERIALIZER_ENABLED) {
            return new ForySerializer<>(typeClass);
        }
        return new KryoSerializer<T>(this.typeClass, config);
    }

    @SuppressWarnings("unchecked")
    @Override
    @PublicEvolving
    public TypeComparator<T> createComparator(
            boolean sortOrderAscending, ExecutionConfig executionConfig) {
        if (isKeyType()) {
            @SuppressWarnings("rawtypes")
            GenericTypeComparator comparator =
                    new GenericTypeComparator(
                            sortOrderAscending, createSerializer(executionConfig), this.typeClass);
            return (TypeComparator<T>) comparator;
        }

        throw new UnsupportedOperationException(
                "Types that do not implement java.lang.Comparable cannot be used as keys.");
    }

    // --------------------------------------------------------------------------------------------

    @Override
    public int hashCode() {
        return typeClass.hashCode();
    }

    @Override
    public boolean canEqual(Object obj) {
        return obj instanceof GenericTypeInfo;
    }

    @Override
    public boolean equals(Object obj) {
        if (obj instanceof GenericTypeInfo) {
            @SuppressWarnings("unchecked")
            GenericTypeInfo<T> genericTypeInfo = (GenericTypeInfo<T>) obj;

            return typeClass == genericTypeInfo.typeClass;
        } else {
            return false;
        }
    }

    @Override
    public String toString() {
        return "GenericType<" + typeClass.getCanonicalName() + ">";
    }
}
