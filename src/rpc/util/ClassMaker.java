// Copyright (c) 2012 Chan Wai Shing
//
// This file is part of BON RPC.
//
// BON RPC is free software: you can redistribute it and/or modify
// it under the terms of the GNU Lesser General Public License as published by
// the Free Software Foundation, either version 3 of the License, or
// (at your option) any later version.
//
// BON RPC is distributed in the hope that it will be useful,
// but WITHOUT ANY WARRANTY; without even the implied warranty of
// MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
// GNU Lesser General Public License for more details.
//
// You should have received a copy of the GNU Lesser General Public License
// along with BON RPC.  If not, see <http://www.gnu.org/licenses/>.
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
import rpc.annotation.Broadcast;
import rpc.annotation.NoRespond;
import rpc.annotation.RequestTypeId;
import rpc.annotation.UserObject;

/**
 * @author Chan Wai Shing <cws1989@gmail.com>
 */
public class ClassMaker {

    protected ClassMaker() {
    }

    public static Class<?> makeClass(Class<?> objClass, Class<?> rpcClass) throws NotFoundException, CannotCompileException {
        ClassPool pool = ClassPool.getDefault();

        // start make class
        String makeClassName = rpcClass.getPackage().getName() + "." + objClass.getSimpleName();
        CtClass evalClass = null;
        try {
            evalClass = pool.get(makeClassName);
            evalClass = pool.makeClass(makeClassName + System.nanoTime());
        } catch (NotFoundException ex) {
            evalClass = pool.makeClass(makeClassName);
        }
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
            methodBody.append(getClassName(method.getReturnType()));
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
                methodBody.append(getClassName(parameterClass));
                methodBody.append(" v");
                methodBody.append(parameterCount);
                parameterCount++;
            }

            // ) throws java.io.IOException, UnsupportedDataTypeException, InvocationFailedException {
            methodBody.append(") throws java.io.IOException, ");
            methodBody.append(rpcClass.getPackage().getName());
            methodBody.append(".codec.exception.UnsupportedDataTypeException, ");
            methodBody.append(rpcClass.getPackage().getName());
            methodBody.append(".exception.InvocationFailedException {");
            methodBody.append("\n");

            boolean userObject = false;
            UserObject userObjectAnnotation = method.getAnnotation(UserObject.class);
            if (userObjectAnnotation != null) {
                userObject = true;
            }

            // Object[] objects = new Object[]{(casting) param1, (casting) param2, ...};
            // or Object[] objects = null;
            int startIndex = userObject ? 2 : 1;
            if (parameterCount != 1 && startIndex < parameterCount) {
                methodBody.append("\t");
                methodBody.append("Object[] objects = new Object[]{");
                for (int i = startIndex; i < parameterCount; i++) {
                    if (i != startIndex) {
                        methodBody.append(", ");
                    }
                    methodBody.append("($w) v");
                    methodBody.append(i);
                }
                methodBody.append("};");
                methodBody.append("\n");
            } else {
                methodBody.append("\t");
                methodBody.append("Object[] objects = new Object[0];");
                methodBody.append("\n");
            }

            // try { return (returnTypeCasting)
            methodBody.append("\t");
            if (!method.getReturnType().equals(void.class)) {
                methodBody.append("try {\n");
                methodBody.append("\t\treturn ($r) ");
            }

            // read annotations
            RequestTypeId requestTypeIdAnnotation = method.getAnnotation(RequestTypeId.class);
            int requestTypeId = requestTypeIdAnnotation.value();

            boolean blocking = false;
            Blocking blockingAnnotation = method.getAnnotation(Blocking.class);
            if (blockingAnnotation != null) {
                blocking = true;
            }

            boolean respond = true;
            NoRespond noRespondAnnotation = method.getAnnotation(NoRespond.class);
            if (noRespondAnnotation != null) {
                respond = false;
            }

            boolean broadcast = false;
            Broadcast broadcastAnnotation = method.getAnnotation(Broadcast.class);
            if (broadcastAnnotation != null) {
                broadcast = true;
            }

            // this.rpc.send(requestId, objects, respond, blocking); }
            methodBody.append("this.rpc.send(");
            methodBody.append(requestTypeId);
            methodBody.append(", objects, ");
            methodBody.append(respond);
            methodBody.append(", ");
            methodBody.append(blocking);
            methodBody.append(", ");
            methodBody.append(broadcast);
            methodBody.append(");");

            // } catch (ClassCastException ex) { Logger.getLogger(this.getClass().getName()).log(Level.SEVERE, null, ex); }
            if (!method.getReturnType().equals(void.class)) {
                methodBody.append("\n\t} catch (ClassCastException ex) {\n\t\tjava.util.logging.Logger.getLogger(this.getClass().getName()).log(java.util.logging.Level.SEVERE, null, ex);\n\t}");
                methodBody.append("\n\treturn null;");
            }

            methodBody.append("\n");
            methodBody.append("}");

            // add method to class
//            System.out.println(methodBody.toString());
            evalClass.addMethod(CtNewMethod.make(methodBody.toString(), evalClass));
        }

        return evalClass.toClass();
    }

    protected static String getClassName(Class<?> clazz) {
        String className = clazz.getName();
        if (className.charAt(0) == '[') {
            int arrayCount = 0;
            String dataTypeName = null;

            char[] classNameChars = className.toCharArray();
            for (int i = 0, iEnd = classNameChars.length - (classNameChars[classNameChars.length - 1] == ';' ? 1 : 0); i < iEnd; i++) {
                if (classNameChars[i] != '[') {
                    switch (classNameChars[i]) {
                        case 'B':
                            dataTypeName = "byte";
                            break;
                        case 'Z':
                            dataTypeName = "boolean";
                            break;
                        case 'C':
                            dataTypeName = "char";
                            break;
                        case 'D':
                            dataTypeName = "double";
                            break;
                        case 'F':
                            dataTypeName = "float";
                            break;
                        case 'I':
                            dataTypeName = "int";
                            break;
                        case 'J':
                            dataTypeName = "long";
                            break;
                        case 'S':
                            dataTypeName = "short";
                            break;
                        case 'L':
                            dataTypeName = new String(classNameChars, i + 1, iEnd - i - 1);
                            break;
                    }
                    break;
                }
                arrayCount++;
            }

            StringBuilder sb = new StringBuilder(dataTypeName.length() + (arrayCount * 2));
            sb.append(dataTypeName);
            while (arrayCount-- > 0) {
                sb.append("[]");
            }
            return sb.toString();
        } else {
            return clazz.getName();
        }
    }

    public static <T> T makeInstance(Class<T> objClass, Class<?> rpcClass, RPC<?> rpc) throws NotFoundException, CannotCompileException, InstantiationException, IllegalAccessException {
        Class<?> instanceClass = makeClass(objClass, rpcClass);
        T instance = objClass.cast(instanceClass.newInstance());
        ((ClassMakerRPCInterface) instance).setRPC(rpc);
        return instance;
    }

    protected interface ClassMakerRPCInterface {

        void setRPC(RPC<?> rpc);
    }
}
