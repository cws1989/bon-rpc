package rpc.util;

import java.lang.reflect.Method;
import javassist.CannotCompileException;
import javassist.ClassPool;
import javassist.CtClass;
import javassist.CtField;
import javassist.CtNewMethod;
import javassist.NotFoundException;
import rpc.RPC;
import rpc.annotation.Blocking;
import rpc.annotation.NoRespond;
import rpc.annotation.RequestTypeId;

/**
 * @author Chan Wai Shing <cws1989@gmail.com>
 */
public class ClassMaker {

    protected ClassMaker() {
    }

    public static Class<?> makeClass(Class<?> objClass, Class<?> rpcClass) throws NotFoundException, CannotCompileException {
        ClassPool pool = ClassPool.getDefault();

        // start make class
        CtClass evalClass = pool.makeClass(rpcClass.getPackage().getName() + "." + objClass.getSimpleName());
        // add interfaces to class, first is the objClass, second is the ClassMakerRPCInterface
        evalClass.addInterface(pool.get(objClass.getName()));
        evalClass.addInterface(pool.get(ClassMakerRPCInterface.class.getName()));

        // add field to remember rpcClass instance
        evalClass.addField(CtField.make("private " + rpcClass.getName() + " rpc;", evalClass));
//        System.out.println("private " + rpcClass.getName() + " rpc;");

        // implement ClassMakerRPCInterface interface method
        evalClass.addMethod(CtNewMethod.make("public void setRPC(" + rpcClass.getName() + " rpc) {\n\tthis.rpc = rpc;\n}", evalClass));
//        System.out.println("protected void setRPC(" + rpcClass.getName() + " rpc) {\n\tthis.rpc = rpc;\n}");

        // build methods of objClass
        Method[] methods = objClass.getMethods();
        for (Method method : methods) {
            // compose method string
            StringBuilder methodBody = new StringBuilder();

            // public returnType methodName(
            methodBody.append("public ");
            methodBody.append(method.getReturnType().getName());
            methodBody.append(" ");
            methodBody.append(method.getName());
            methodBody.append("(");

            // args[]
            int parameterCount = 1;
            Class[] parametersClasses = method.getParameterTypes();
            for (Class<?> parameterClass : parametersClasses) {
                if (parameterCount != 1) {
                    methodBody.append(", ");
                }
                methodBody.append(parameterClass.getName());
                methodBody.append(" v");
                methodBody.append(parameterCount);
                parameterCount++;
            }

            // ) throws java.io.IOException, UnsupportedDataTypeException {
            methodBody.append(") throws java.io.IOException, ");
            methodBody.append(rpcClass.getPackage().getName());
            methodBody.append(".codec.exception.UnsupportedDataTypeException {");
            methodBody.append("\n");

            // Object[] objects = new Object[]{(casting) param1, (casting) param2, ...};
            // or Object[] objects = null;
            if (parameterCount != 1) {
                methodBody.append("\t");
                methodBody.append("Object[] objects = new Object[]{");
                for (int i = 1; i < parameterCount; i++) {
                    if (i != 1) {
                        methodBody.append(", ");
                    }
                    methodBody.append("($w) v");
                    methodBody.append(i);
                }
                methodBody.append("};");
                methodBody.append("\n");
            } else {
                methodBody.append("\t");
                methodBody.append("Object[] objects = null;");
                methodBody.append("\n");
            }

            // return (returnTypeCasting)
            methodBody.append("\t");
            if (!method.getReturnType().equals(void.class)) {
                methodBody.append("return ($r) ");
            }

            // read annotations
            int requestId = -1;
            RequestTypeId requestTypeIdAnnotation = method.getAnnotation(RequestTypeId.class);
            if (requestTypeIdAnnotation != null && requestTypeIdAnnotation.value() > 0) {
                requestId = requestTypeIdAnnotation.value();
            } else {
                continue;
            }

            boolean respond = true;
            NoRespond noRespondAnnotation = method.getAnnotation(NoRespond.class);
            if (noRespondAnnotation != null) {
                respond = false;
            }

            boolean blocking = false;
            Blocking blockingAnnotation = method.getAnnotation(Blocking.class);
            if (blockingAnnotation != null) {
                blocking = blockingAnnotation.value();
            }

            // this.rpc.send(requestId, objects, respond, blocking); }
            methodBody.append("this.rpc.send(");
            methodBody.append(requestId);
            methodBody.append(", objects, ");
            methodBody.append(respond);
            methodBody.append(", ");
            methodBody.append(blocking);
            methodBody.append(");");
            methodBody.append("\n");
            methodBody.append("}");

            // add method to class
            evalClass.addMethod(CtNewMethod.make(methodBody.toString(), evalClass));
//            System.out.println(methodBody.toString());
        }

        return evalClass.toClass();
    }

    public static <T> T makeInstance(Class<T> objClass, Class<?> rpcClass, RPC rpc) throws NotFoundException, CannotCompileException, InstantiationException, IllegalAccessException {
        Class<?> instanceClass = makeClass(objClass, rpcClass);
        T instance = objClass.cast(instanceClass.newInstance());
        ((ClassMakerRPCInterface) instance).setRPC(rpc);
        return instance;
    }

    protected interface ClassMakerRPCInterface {

        void setRPC(RPC rpc);
    }
//    public static void main(String[] args) throws CannotCompileException, NotFoundException, IllegalAccessException, InstantiationException {
//        RemoteInterface remoteInterface = makeInstance(RemoteInterface.class, RPC.class, null);
//        Class<?> clazz = makeClass(RemoteInterface.class, RPC.class);
//        RemoteInterface remoteInterface = (RemoteInterface) clazz.newInstance();
//    }
}
