/*
   Copyright 2009 Attila Szegedi

   Licensed under the Apache License, Version 2.0 (the "License");
   you may not use this file except in compliance with the License.
   You may obtain a copy of the License at

       http://www.apache.org/licenses/LICENSE-2.0

   Unless required by applicable law or agreed to in writing, software
   distributed under the License is distributed on an "AS IS" BASIS,
   WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
   See the License for the specific language governing permissions and
   limitations under the License.
*/
package org.dynalang.dynalink.support;

import java.dyn.MethodHandle;
import java.dyn.MethodHandles;
import java.dyn.MethodType;
import java.dyn.WrongMethodTypeException;
import java.util.LinkedList;
import java.util.List;

import org.dynalang.dynalink.GuardedInvocation;
import org.dynalang.dynalink.GuardingTypeConverterFactory;
import org.dynalang.dynalink.LinkerServices;
import org.dynalang.dynalink.beans.support.TypeUtilities;

/**
 * A factory for type converters. This class is the main implementation behind
 * the {@link LinkerServices#convertArguments(MethodHandle, MethodType)}. It
 * manages the known {@link GuardingTypeConverterFactory} instances and creates
 * appropriate converters for method handles.
 * @author Attila Szegedi
 * @version $Id: $
 */
public class TypeConverterFactory {

    private final GuardingTypeConverterFactory[] factories;
    private final ClassMap<ClassMap<MethodHandle>> converters;
    
    /**
     * Creates a new type converter factory from the available 
     * {@link GuardingTypeConverterFactory} instances.
     * @param factories the {@link GuardingTypeConverterFactory} instances to
     * compose.
     * @param classLoader the class loader governing the strong 
     * referenceability of cached information, see {@link ClassMap}.
     */
    public TypeConverterFactory(
            Iterable<GuardingTypeConverterFactory> factories, 
            ClassLoader classLoader) {
        converters = new ClassMap<ClassMap<MethodHandle>>(classLoader);
        final List<GuardingTypeConverterFactory> l = new LinkedList<GuardingTypeConverterFactory>();
        for (GuardingTypeConverterFactory factory : factories) {
            l.add(factory);
        }
        this.factories = l.toArray(new GuardingTypeConverterFactory[l.size()]);
        
    }

    /**
     * Creates an implementation of {@link LinkerServices} that relies on this
     * type converter factory.
     * @return an implementation of {@link LinkerServices}.
     */
    public LinkerServices createLinkerServices() {
        return new LinkerServices() {

            public boolean canConvert(Class<?> from, Class<?> to) {
                return TypeConverterFactory.this.canConvert(from, to);
            }

            public MethodHandle convertArguments(MethodHandle handle,
                    MethodType fromType)
            {
                return TypeConverterFactory.this.convertArguments(handle, 
                        fromType);
            }
            
        };
    }
    
    /**
     * Similar to {@link MethodHandles#convertArguments(MethodHandle, MethodType)}
     * except it also hooks in method handles produced by 
     * {@link GuardingTypeConverterFactory} implementations, providing for
     * language-specific type coercing of parameters. It will apply 
     * {@link MethodHandles#convertArguments(MethodHandle, MethodType)} for 
     * all primitive-to-primitive, wrapper-to-primitive, primitive-to-wrapper
     * conversions as well as for all upcasts. For all other conversions, it'll
     * insert {@link MethodHandles#filterArguments(MethodHandle, MethodHandle...)}
     * with composite filters provided by {@link GuardingTypeConverterFactory}
     * implementations. It doesn't use language-specific conversions on the
     * return type.
     * @param handle target method handle
     * @param fromType the types of source arguments
     * @return a method handle that is a suitable combination of 
     * {@link MethodHandles#convertArguments(MethodHandle, MethodType)} and
     * {@link MethodHandles#filterArguments(MethodHandle, MethodHandle...)} 
     * with {@link GuardingTypeConverterFactory} produced type converters as 
     * filters.
     */
    public MethodHandle convertArguments(final MethodHandle handle, 
            final MethodType fromType) {
        final MethodType toType = handle.type();
        final int l = toType.parameterCount();
        if(l != fromType.parameterCount()) {
            throw new WrongMethodTypeException("Parameter counts differ");
        }
        final MethodHandle[] converters = new MethodHandle[l];
        for(int i = 0; i < l; ++i) {
            final Class<?> fromParamType = fromType.parameterType(i);
            final Class<?> toParamType = toType.parameterType(i);
            if(!canAutoConvert(fromParamType, toParamType)) {
                converters[i] = getTypeConverter(fromParamType, toParamType);
            }
        }
        
        return MethodHandles.convertArguments(MethodHandles.filterArguments(
                handle, converters), fromType);
    }

    /**
     * Returns true if there might exist a conversion between the requested
     * types (either an automatic JVM conversion, or one provided by any 
     * available {@link GuardingTypeConverterFactory}), or false if there 
     * definitely does not exist a conversion between the requested types. Note
     * that returning true does not guarantee that the conversion will succeed
     * at runtime (notably, if the "from" or "to" types are sufficiently 
     * generic), but returning false guarantees that it would fail.
     * @param from the source type for the conversion
     * @param to the target type for the conversion
     * @return true if there can be a conversion, false if there can not.
     */
    public boolean canConvert(final Class<?> from, final Class<?> to) {
        return canAutoConvert(from, to) || getTypeConverter(from, to) != null; 
    }
    
    /**
     * Determines whether it's safe to perform an automatic conversion 
     * between the source and target class.
     * @param fromType convert from this class
     * @param toType convert to this class
     * @return true if it's safe to let MethodHandles.convertArguments() to
     * handle this conversion.
     */
    private static boolean canAutoConvert(final Class<?> fromType, 
            final Class<?> toType)
    {
        if(fromType.isPrimitive()) {
            if(fromType == Void.TYPE) {
                return true;
            }
            if(toType.isPrimitive()) {
                if(toType == Void.TYPE) {
                    return true;
                }
                return canAutoConvertPrimitives(fromType, toType);
            }
            return canAutoConvertPrimitiveToReference(fromType, toType);
        }
        if(toType.isPrimitive()) {
            if(toType == Void.TYPE) {
                return true;
            }
            return canAutoConvertPrimitiveToReference(toType, fromType);
        }
        // In all other cases, only allow automatic conversion from a class to 
        // its superclass or superinterface.
        return toType.isAssignableFrom(fromType);
    }

    private static boolean canAutoConvertPrimitiveToReference(
            final Class<?> primitiveType, final Class<?> refType)
    {
        return TypeUtilities.isAssignableFromBoxedPrimitive(refType) && 
            ((primitiveType != Byte.TYPE && primitiveType != Boolean.TYPE) || 
                    refType != Character.class); 
    }

    private static boolean canAutoConvertPrimitives(Class<?> fromType, Class<?> toType) {
        // the only cast conversion not allowed between non-boolean primitives 
        // is byte->char, all other narrowing and widening conversions are 
        // allowed. boolean is converted to byte first, so same applies to it.
        return (fromType != Byte.TYPE && fromType != Boolean.TYPE) || toType != Character.TYPE;
    }
    
    private MethodHandle getTypeConverter(Class<?> sourceType, Class<?> targetType) {
        ClassMap<MethodHandle> converterMap = converters.get(sourceType);
        if(converterMap == null) {
            converterMap = new ClassMap<MethodHandle>(converters.getClassLoader());
            converters.put(sourceType, converterMap);
        }
        MethodHandle converter = converterMap.get(targetType);
        if(converter == null) {
            converter = createConverter(sourceType, targetType);
            converterMap.put(targetType, converter);
        }
        return converter == IDENTITY_CONVERSION ? null : converter;
    }
    
    private MethodHandle createConverter(Class<?> sourceType, Class<?> targetType) {
        final MethodType type = MethodType.methodType(targetType, sourceType);
        final MethodHandle identity = MethodHandles.convertArguments(
                IDENTITY_CONVERSION, type); 
        MethodHandle last = identity;
        for(int i = factories.length; i --> 0;) {
            final GuardedInvocation next = factories[i].convertToType(
                    sourceType, targetType);
            if(next != null) {
                next.assertType(type);
                last = MethodHandles.guardWithTest(next.getGuard(), 
                        next.getInvocation(), last);
            }
        }
        return last == identity ? IDENTITY_CONVERSION : last;
    }
 
    private static final MethodHandle IDENTITY_CONVERSION = 
        MethodHandles.lookup().findStatic(TypeConverterFactory.class, 
                "_identityConversion", MethodType.methodType(Object.class, 
                        Object.class));
    
    /**
     * This method is public for implementation reasons. Do not invoke it 
     * directly. Returns the object passed in. 
     * @param o the object
     * @return the object
     */
    public static Object _identityConversion(Object o) {
        return o;
    }
}