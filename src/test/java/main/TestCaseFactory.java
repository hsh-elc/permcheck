package main;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.METHOD)
public @interface TestCaseFactory {

    /**
     * declares the spec, that is related to this factory method, e. g. "deny.reflectionAccessDeclaredMembers".
     * If the method os not related to any specific spec, then declare relatedSpec = "".
     */
    public String relatedSpec(); 
}
