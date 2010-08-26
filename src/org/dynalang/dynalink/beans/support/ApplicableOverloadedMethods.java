package org.dynalang.dynalink.beans.support;

import java.dyn.MethodType;
import java.util.LinkedList;
import java.util.List;

/**
 * Represents overloaded methods applicable to a specific call site signature.  
 * @author Attila Szegedi
 * @version $Id: $
 */
public class ApplicableOverloadedMethods
{
    private final List<MethodHandleEx> methods;
    private final boolean varArgs;
    
    /**
     * Creates a new ApplicableOverloadedMethods instance 
     * @param methods a list of all overloaded methods with the same name for
     * a class.
     * @param callSiteType the type of the call site 
     * @param test applicability test. One of {@link #APPLICABLE_BY_SUBTYPING},
     * {@link #APPLICABLE_BY_METHOD_INVOCATION_CONVERSION}, or 
     * {@link #APPLICABLE_BY_VARIABLE_ARITY}.
     */
    public ApplicableOverloadedMethods(final List<MethodHandleEx> methods, 
            final MethodType callSiteType, final ApplicabilityTest test) {
        this.methods = new LinkedList<MethodHandleEx>();
        for (MethodHandleEx m : methods) {
            if(test.isApplicable(callSiteType, m)) {
                this.methods.add(m);
            }
        }
        varArgs = test == APPLICABLE_BY_VARIABLE_ARITY;
    }
    
    /**
     * Retrieves all the methods this object holds.
     * @return list of all methods.
     */
    public List<MethodHandleEx> getMethods() {
        return methods;
    }
    
    /**
     * Returns a list of all methods in this objects that are maximally 
     * specific.
     * @return a list of maximally specific methods.
     */
    public List<MethodHandleEx> findMaximallySpecificMethods() {
        return MaximallySpecific.getMaximallySpecificMethods(methods, TF, 
                varArgs);
    }
    
    private static MaximallySpecific.TypeFunction<MethodHandleEx> TF = 
        new MaximallySpecific.TypeFunction<MethodHandleEx>() {
            public MethodType type(MethodHandleEx mh) {
                return mh.getMethodHandle().type();
            };
        };

    /**
     * Finds the maximally specific methods (per JLS 15.12.2.5).
     * @returns the list of maximally specific methods. The list can be empty,
     * but it will never be null.
     */
    public abstract static class ApplicabilityTest {
        abstract boolean isApplicable(MethodType callSiteType, MethodHandleEx methodEx);
    }
    
    /**
     * Implements the applicability-by-subtyping test from JLS 15.12.2.2.
     */
    public static final ApplicabilityTest APPLICABLE_BY_SUBTYPING = new ApplicabilityTest() 
    {
        @Override
        boolean isApplicable(MethodType callSiteType, MethodHandleEx methodEx) {
            final MethodType methodType = methodEx.methodHandle.type(); 
            final int methodArity = methodType.parameterCount(); 
            if(methodArity != callSiteType.parameterCount()) {
                return false;
            }
            // 0th arg is receiver; it doesn't matter for overload resolution.
            for(int i = 1; i < methodArity; ++i) {
                if(!TypeUtilities.isSubtype(callSiteType.parameterType(i), 
                        methodType.parameterType(i))) {
                    return false;
                }
            }
            return true;
        }
    };

    /**
     * Implements the applicability-by-method-invocation-conversion test from 
     * JLS 15.12.2.3.
     */
    public static final ApplicabilityTest APPLICABLE_BY_METHOD_INVOCATION_CONVERSION = new ApplicabilityTest() 
    {
        @Override
        boolean isApplicable(MethodType callSiteType, MethodHandleEx methodEx) {
            final MethodType methodType = methodEx.methodHandle.type(); 
            final int methodArity = methodType.parameterCount(); 
            if(methodArity != callSiteType.parameterCount()) {
                return false;
            }
            // 0th arg is receiver; it doesn't matter for overload resolution.
            for(int i = 1; i < methodArity; ++i) {
                if(!TypeUtilities.isMethodInvocationConvertible(
                        callSiteType.parameterType(i), 
                        methodType.parameterType(i)))
                {
                    return false;
                }
            }
            return true;
        }
    };

    /**
     * Implements the applicability-by-variable-arity test from JLS 15.12.2.4.
     */
    public static final ApplicabilityTest APPLICABLE_BY_VARIABLE_ARITY = new ApplicabilityTest() 
    {
        @Override
        boolean isApplicable(MethodType callSiteType, MethodHandleEx methodEx) {
            if(!methodEx.varArgs) {
                return false;
            }
            final MethodType methodType = methodEx.methodHandle.type(); 
            final int methodArity = methodType.parameterCount();
            final int fixArity = methodArity - 1;
            final int callSiteArity = callSiteType.parameterCount();
            if(fixArity > callSiteArity) {
                return false;
            }
            // 0th arg is receiver; it doesn't matter for overload resolution.
            for(int i = 1; i < fixArity; ++i) {
                if(!TypeUtilities.isMethodInvocationConvertible(
                        callSiteType.parameterType(i), 
                        methodType.parameterType(i)))
                {
                    return false;
                }
            }
            final Class<?> varArgType = methodType.parameterType(
                    fixArity).getComponentType();
            for(int i = fixArity; i < callSiteArity; ++i) {
                if(!TypeUtilities.isMethodInvocationConvertible(
                        callSiteType.parameterType(i), varArgType))
                {
                    return false;
                }
            }
            return true;
        }
    };
}