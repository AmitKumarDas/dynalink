package org.dynalang.dynalink.beans;

import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;

import org.dynalang.dynalink.CallSiteDescriptor;
import org.dynalang.dynalink.linker.GuardedInvocation;
import org.dynalang.dynalink.linker.LinkRequest;
import org.dynalang.dynalink.linker.LinkerServices;
import org.dynalang.dynalink.linker.TypeBasedGuardingDynamicLinker;
import org.dynalang.dynalink.support.Guards;

/**
 * Simple linker that implements the "dyn:call" operation for {@link DynamicMethod} objects - the objects returned by
 * "dyn:getMethod" from {@link AbstractJavaLinker}.
 */
class DynamicMethodLinker implements TypeBasedGuardingDynamicLinker {
    @Override
    public boolean canLinkType(Class<?> type) {
        return DynamicMethod.class.isAssignableFrom(type);
    };

    @Override
    public GuardedInvocation getGuardedInvocation(LinkRequest linkRequest, LinkerServices linkerServices) {
        final Object receiver = linkRequest.getReceiver();
        if(!(receiver instanceof DynamicMethod)) {
            return null;
        }
        final CallSiteDescriptor desc = linkRequest.getCallSiteDescriptor();
        if(desc.getNameTokenCount() != 2 && desc.getNameToken(CallSiteDescriptor.SCHEME) != "dyn")  {
            return null;
        }
        final String operator = desc.getNameToken(CallSiteDescriptor.OPERATOR);
        if(operator == "call") {
            final MethodType type = desc.getMethodType();
            return new GuardedInvocation(MethodHandles.dropArguments(((DynamicMethod)receiver).getInvocation(
                    type.dropParameterTypes(0, 1), linkerServices), 0, type.parameterType(0)), Guards.getIdentityGuard(
                            receiver));
        }
        return null;
    }
}
