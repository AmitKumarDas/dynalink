/*
   Copyright 2009-2012 Attila Szegedi

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

import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.util.logging.Level;
import java.util.logging.Logger;

import org.dynalang.dynalink.linker.LinkerServices;

/**
 * Utility methods for creating typical guards. TODO: introduce reasonable caching of created guards.
 *
 * @author Attila Szegedi
 */
public class Guards {
    private static final Logger LOG = Logger
            .getLogger(Guards.class.getName(), "org.dynalang.dynalink.support.messages");

    private Guards() {
    }

    /**
     * Creates a guard method handle with arguments of a specified type, but with boolean return value. When invoked, it
     * returns true if the first argument is of the specified class (exactly of it, not a subclass). The rest of the
     * arguments will be ignored.
     *
     * @param clazz the class of the first argument to test for
     * @param type the method type
     * @return a method handle testing whether its first argument is of the specified class.
     */
    @SuppressWarnings("boxing")
    public static MethodHandle isOfClass(Class<?> clazz, MethodType type) {
        final Class<?> declaredType = type.parameterType(0);
        if(clazz == declaredType) {
            LOG.log(Level.WARNING, "isOfClassGuardAlwaysTrue", new Object[] { clazz.getName(), 0, type });
            return constantTrue(type);
        }
        if(!declaredType.isAssignableFrom(clazz)) {
            LOG.log(Level.WARNING, "isOfClassGuardAlwaysFalse", new Object[] { clazz.getName(), 0, type });
            return constantFalse(type);
        }
        return getClassBoundArgumentTest(IS_OF_CLASS, clazz, 0, type);
    }

    /**
     * Creates a method handle with arguments of a specified type, but with boolean return value. When invoked, it
     * returns true if the first argument is instance of the specified class or its subclass). The rest of the arguments
     * will be ignored.
     *
     * @param clazz the class of the first argument to test for
     * @param type the method type
     * @return a method handle testing whether its first argument is of the specified class or subclass.
     */
    public static MethodHandle isInstance(Class<?> clazz, MethodType type) {
        return isInstance(clazz, 0, type);
    }

    /**
     * Creates a method handle with arguments of a specified type, but with boolean return value. When invoked, it
     * returns true if the n'th argument is instance of the specified class or its subclass). The rest of the arguments
     * will be ignored.
     *
     * @param clazz the class of the first argument to test for
     * @param pos the position on the argument list to test
     * @param type the method type
     * @return a method handle testing whether its first argument is of the specified class or subclass.
     */
    @SuppressWarnings("boxing")
    public static MethodHandle isInstance(Class<?> clazz, int pos, MethodType type) {
        final Class<?> declaredType = type.parameterType(pos);
        if(clazz.isAssignableFrom(declaredType)) {
            LOG.log(Level.WARNING, "isInstanceGuardAlwaysTrue", new Object[] { clazz.getName(), pos, type });
            return constantTrue(type);
        }
        if(!declaredType.isAssignableFrom(clazz)) {
            LOG.log(Level.WARNING, "isInstanceGuardAlwaysFalse", new Object[] { clazz.getName(), pos, type });
            return constantFalse(type);
        }
        return getClassBoundArgumentTest(IS_INSTANCE, clazz, pos, type);
    }

    /**
     * Creates a method handle that returns true if the argument in the specified position is a Java array.
     *
     * @param pos the position in the argument lit
     * @param type the method type of the handle
     * @return a method handle that returns true if the argument in the specified position is a Java array; the rest of
     * the arguments are ignored.
     */
    @SuppressWarnings("boxing")
    public static MethodHandle isArray(int pos, MethodType type) {
        final Class<?> declaredType = type.parameterType(pos);
        if(declaredType.isArray()) {
            LOG.log(Level.WARNING, "isArrayGuardAlwaysTrue", new Object[] { pos, type });
            return constantTrue(type);
        }
        if(!declaredType.isAssignableFrom(Object[].class)) {
            LOG.log(Level.WARNING, "isArrayGuardAlwaysFalse", new Object[] { pos, type });
            return constantFalse(type);
        }
        return asType(IS_ARRAY, pos, type);
    }

    /**
     * Return true if it is safe to strongly reference a class from the referred class loader from a class associated
     * with the referring class loader without risking a class loader memory leak.
     *
     * @param referrerLoader the referrer class loader
     * @param referredLoader the referred class loader
     * @return true if it is safe to strongly reference the class
     */
    public static boolean canReferenceDirectly(ClassLoader referrerLoader, final ClassLoader referredLoader) {
        if(referredLoader == null) {
            // Can always refer directly to a system class
            return true;
        }
        if(referrerLoader == null) {
            // System classes can't refer directly to any non-system class
            return false;
        }
        // Otherwise, can only refer directly to classes residing in same or
        // parent class loader.

        ClassLoader referrer = referrerLoader;
        do {
            if(referrer == referredLoader) {
                return true;
            }
            referrer = referrer.getParent();
        } while(referrer != null);
        return false;
    }

    private static MethodHandle getClassBoundArgumentTest(MethodHandle test, Class<?> clazz, int pos, MethodType type) {
        // Bind the class to the first argument of the test
        return asType(test.bindTo(clazz), pos, type);
    }

    /**
     * Takes a guard-test method handle, and adapts it to the requested type, returning a boolean. Only applies
     * conversions as per {@link MethodHandle#asType(MethodType)}.
     * @param test the test method handle
     * @param type the type to adapt the method handle to
     * @return the adapted method handle
     */
    public static MethodHandle asType(MethodHandle test, MethodType type) {
        return test.asType(getTestType(test, type));
    }

    /**
     * Takes a guard-test method handle, and adapts it to the requested type, returning a boolean. Applies the passed
     * {@link LinkerServices} object's {@link LinkerServices#asType(MethodHandle, MethodType)}.
     * @param linkerServices the linker services to use for type conversions
     * @param test the test method handle
     * @param type the type to adapt the method handle to
     * @return the adapted method handle
     */
    public static MethodHandle asType(LinkerServices linkerServices, MethodHandle test, MethodType type) {
        return linkerServices.asType(test, getTestType(test, type));
    }

    private static MethodType getTestType(MethodHandle test, MethodType type) {
        return type.dropParameterTypes(test.type().parameterCount(),
                type.parameterCount()).changeReturnType(boolean.class);
    }

    private static MethodHandle asType(MethodHandle test, int pos, MethodType type) {
        assert test != null;
        assert type != null;
        assert type.parameterCount() > 0;
        assert pos >= 0 && pos < type.parameterCount();
        assert test.type().parameterCount() == 1;
        assert test.type().returnType() == Boolean.TYPE;
        return MethodHandles.permuteArguments(test.asType(test.type().changeParameterType(0, type.parameterType(pos))),
                type.changeReturnType(Boolean.TYPE), new int[] { pos });
    }

    private static final MethodHandle IS_OF_CLASS = new Lookup(MethodHandles.lookup()).findStatic(Guards.class,
            "isOfClass", MethodType.methodType(Boolean.TYPE, Class.class, Object.class));

    private static final MethodHandle IS_INSTANCE = Lookup.PUBLIC.findVirtual(Class.class, "isInstance",
            MethodType.methodType(Boolean.TYPE, Object.class));

    private static final MethodHandle IS_ARRAY = new Lookup(MethodHandles.lookup()).findStatic(Guards.class, "isArray",
            MethodType.methodType(Boolean.TYPE, Object.class));

    private static final MethodHandle IS_IDENTICAL = new Lookup(MethodHandles.lookup()).findStatic(Guards.class,
            "isIdentical", MethodType.methodType(Boolean.TYPE, Object.class, Object.class));

    private static final MethodHandle IS_NULL = new Lookup(MethodHandles.lookup()).findStatic(Guards.class,
            "isNull", MethodType.methodType(Boolean.TYPE, Object.class));

    private static final MethodHandle IS_NOT_NULL = new Lookup(MethodHandles.lookup()).findStatic(Guards.class,
            "isNotNull", MethodType.methodType(Boolean.TYPE, Object.class));

    /**
     * Creates a guard method that tests its only argument for being of an exact particular class.
     * @param clazz the class to test for.
     * @return the desired guard method.
     */
    public static MethodHandle getClassGuard(Class<?> clazz) {
        return IS_OF_CLASS.bindTo(clazz);
    }

    /**
     * Creates a guard method that tests its only argument for being an instance of a particular class.
     * @param clazz the class to test for.
     * @return the desired guard method.
     */
    public static MethodHandle getInstanceOfGuard(Class<?> clazz) {
        return IS_INSTANCE.bindTo(clazz);
    }

    /**
     * Creates a guard method that tests its only argument for being referentially identical to another object
     * @param obj the object used as referential identity test
     * @return the desired guard method.
     */
    public static MethodHandle getIdentityGuard(Object obj) {
        return IS_IDENTICAL.bindTo(obj);
    }

    /**
     * Returns a guard that tests whether the first argument is null.
     * @return a guard that tests whether the first argument is null.
     */
    public static MethodHandle isNull() {
        return IS_NULL;
    }

    /**
     * Returns a guard that tests whether the first argument is not null.
     * @return a guard that tests whether the first argument is not null.
     */
    public static MethodHandle isNotNull() {
        return IS_NOT_NULL;
    }

    @SuppressWarnings("unused")
    private static boolean isNull(Object obj) {
        return obj == null;
    }

    @SuppressWarnings("unused")
    private static boolean isNotNull(Object obj) {
        return obj != null;
    }

    @SuppressWarnings("unused")
    private static boolean isArray(Object o) {
        return o != null && o.getClass().isArray();
    }

    @SuppressWarnings("unused")
    private static boolean isOfClass(Class<?> c, Object o) {
        return o != null && o.getClass() == c;
    }

    @SuppressWarnings("unused")
    private static boolean isIdentical(Object o1, Object o2) {
        return o1 == o2;
    }

    private static MethodHandle constantTrue(MethodType type) {
        return constantBoolean(Boolean.TRUE, type);
    }

    private static MethodHandle constantFalse(MethodType type) {
        return constantBoolean(Boolean.FALSE, type);
    }

    private static MethodHandle constantBoolean(Boolean value, MethodType type) {
        return MethodHandles.permuteArguments(MethodHandles.constant(Boolean.TYPE, value),
                type.changeReturnType(Boolean.TYPE));
    }
}