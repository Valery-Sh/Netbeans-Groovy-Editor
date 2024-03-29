/*
 * To change this template, choose Tools | Templates and open the template in
 * the editor.
 */
package org.netbeans.modules.groovy.editor.stc;
/*
 * Copyright 2003-2010 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not
 * use this file except in compliance with the License. You may obtain a copy of
 * the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
 * License for the specific language governing permissions and limitations under
 * the License.
 */

import org.codehaus.groovy.ast.*;
import org.codehaus.groovy.ast.expr.*;
import org.codehaus.groovy.ast.tools.GenericsUtils;
import org.codehaus.groovy.runtime.DefaultGroovyMethods;

import java.util.*;
import java.util.regex.Matcher;

import static org.codehaus.groovy.ast.ClassHelper.*;
import static org.codehaus.groovy.syntax.Types.*;

/**
 * Static support methods for {@link StaticTypeCheckingVisitor}.
 */
abstract class StaticTypeCheckingSupportMy {

    final static Map<String, List<MethodNode>> VIRTUAL_DGM_METHODS = getDGMMethods();
    final static ClassNode Collection_TYPE = makeWithoutCaching(Collection.class);
    final static ClassNode Matcher_TYPE = makeWithoutCaching(Matcher.class);
    final static ClassNode ArrayList_TYPE = makeWithoutCaching(ArrayList.class);
    final static Map<ClassNode, Integer> NUMBER_TYPES = Collections.unmodifiableMap(
            new HashMap<ClassNode, Integer>() {

                {
                    put(ClassHelper.byte_TYPE, 0);
                    put(ClassHelper.Byte_TYPE, 0);
                    put(ClassHelper.short_TYPE, 1);
                    put(ClassHelper.Short_TYPE, 1);
                    put(ClassHelper.int_TYPE, 2);
                    put(ClassHelper.Integer_TYPE, 2);
                    put(ClassHelper.Long_TYPE, 3);
                    put(ClassHelper.long_TYPE, 3);
                    put(ClassHelper.float_TYPE, 4);
                    put(ClassHelper.Float_TYPE, 4);
                    put(ClassHelper.double_TYPE, 5);
                    put(ClassHelper.Double_TYPE, 5);
                }
            });
    /**
     * This comparator is used when we return the list of methods from DGM which
     * name correspond to a given name. As we also lookup for DGM methods of
     * superclasses or interfaces, it may be possible to find two methods which
     * have the same name and the same arguments. In that case, we should not add
     * the method from superclass or interface otherwise the system won't be able
     * to select the correct method, resulting in an ambiguous method selection
     * for similar methods.
     */
    private static final Comparator<MethodNode> DGM_METHOD_NODE_COMPARATOR = new Comparator<MethodNode>() {

        public int compare(final MethodNode o1, final MethodNode o2) {
            if (o1.getName().equals(o2.getName())) {
                Parameter[] o1ps = o1.getParameters();
                Parameter[] o2ps = o2.getParameters();
                if (o1ps.length == o2ps.length) {
                    boolean allEqual = true;
                    for (int i = 0; i < o1ps.length && allEqual; i++) {
                        allEqual = o1ps[i].getType().equals(o2ps[i].getType());
                    }
                    if (allEqual) {
                        return 0;
                    }
                } else {
                    return o1ps.length - o2ps.length;
                }
            }
            return 1;
        }
    };

    /**
     * Returns true for expressions of the form x[...]
     * @param expression an expression
     * @return true for array access expressions
     */
    static boolean isArrayAccessExpression(Expression expression) {
        return expression instanceof BinaryExpression && isArrayOp(((BinaryExpression) expression).getOperation().getType());
    }

    /**
     * Called on method call checks in order to determine if a method call corresponds to the
     * idiomatic o.with { ... } structure
     * @param name name of the method called
     * @param callArguments arguments of the method
     * @return true if the name is "with" and arguments consist of a single closure
     */
    static boolean isWithCall(final String name, final Expression callArguments) {
        boolean isWithCall = "with".equals(name) && callArguments instanceof ArgumentListExpression;
        if (isWithCall) {
            ArgumentListExpression argList = (ArgumentListExpression) callArguments;
            List<Expression> expressions = argList.getExpressions();
            isWithCall = expressions.size() == 1 && expressions.get(0) instanceof ClosureExpression;
        }
        return isWithCall;
    }

    /**
     * Given a variable expression, returns the ultimately accessed variable.
     * @param ve a variable expression
     * @return the target variable
     */
    static Variable findTargetVariable(VariableExpression ve) {
        final Variable accessedVariable = ve.getAccessedVariable();
        if (accessedVariable != ve) {
            if (accessedVariable instanceof VariableExpression) {
                return findTargetVariable((VariableExpression) accessedVariable);
            }
        }
        return accessedVariable;
    }

    /**
     * Returns a map which contains, as the key, the name of a class. The value
     * consists of a list of MethodNode, one for each default groovy method found
     * which is applicable for this class.
     * @return
     */
    private static Map<String, List<MethodNode>> getDGMMethods() {
        Map<String, List<MethodNode>> methods = new HashMap<String, List<MethodNode>>();
        ClassNode cn = ClassHelper.makeWithoutCaching(DefaultGroovyMethods.class, true);
        for (MethodNode metaMethod : cn.getMethods()) {
            Parameter[] types = metaMethod.getParameters();
            if (metaMethod.isStatic() && metaMethod.isPublic() && types.length > 0) {
                Parameter[] parameters = new Parameter[types.length - 1];
                System.arraycopy(types, 1, parameters, 0, parameters.length);
                MethodNode node = new MethodNode(
                        metaMethod.getName(),
                        metaMethod.getModifiers(),
                        metaMethod.getReturnType(),
                        parameters,
                        ClassNode.EMPTY_ARRAY, null);
                node.setGenericsTypes(metaMethod.getGenericsTypes());
                ClassNode declaringClass = types[0].getType();
                String declaringClassName = declaringClass.getName();
                node.setDeclaringClass(declaringClass);

                List<MethodNode> nodes = methods.get(declaringClassName);
                if (nodes == null) {
                    nodes = new LinkedList<MethodNode>();
                    methods.put(declaringClassName, nodes);
                }
                nodes.add(node);
            }
        }
        return methods;
    }

    static Set<MethodNode> findDGMMethodsForClassNode(ClassNode clazz, String name) {
        TreeSet<MethodNode> accumulator = new TreeSet<MethodNode>(DGM_METHOD_NODE_COMPARATOR);
        findDGMMethodsForClassNode(clazz, name, accumulator);
        return accumulator;
    }

    static void findDGMMethodsForClassNode(ClassNode clazz, String name, TreeSet<MethodNode> accumulator) {
        List<MethodNode> fromDGM = VIRTUAL_DGM_METHODS.get(clazz.getName());
        if (fromDGM != null) {
            for (MethodNode node : fromDGM) {
                if (node.getName().equals(name)) {
                    accumulator.add(node);
                }
            }
        }
        for (ClassNode node : clazz.getInterfaces()) {
            findDGMMethodsForClassNode(node, name, accumulator);
        }
        if (clazz.getSuperClass() != null) {
            findDGMMethodsForClassNode(clazz.getSuperClass(), name, accumulator);
        } else if (!clazz.equals(ClassHelper.OBJECT_TYPE)) {
            findDGMMethodsForClassNode(ClassHelper.OBJECT_TYPE, name, accumulator);
        }
    }

    /**
     * Checks that arguments and parameter types match.
     * @param params method parameters
     * @param args type arguments
     * @return -1 if arguments do not match, 0 if arguments are of the exact type and >0 when one or more argument is
     * not of the exact type but still match
     */
    static int allParametersAndArgumentsMatch(Parameter[] params, ClassNode[] args) {
        if (params == null) {
            return args.length == 0 ? 0 : -1;
        }
        int dist = 0;
        // we already know the lengths are equal
        for (int i = 0; i < params.length; i++) {
            if (params[i].getName().startsWith("mymy")) {
                ClassNode p = params[i].getType();
                System.out.println("***** SUPPORT PARAM  params[" + i + "]  : name = " + params[i].getName() + "; type.name=" + p.getName() + "; type=" + p);
                System.out.println("***** SUPPORT ARG      args[" + i + "]  : name = " + args[i].getName() + "; type.name=" + args[i].getName() + "; type=" + args[i]);
                if (params[i].getType().getGenericsTypes() != null) {
                    GenericsType gt = params[i].getType().getGenericsTypes()[0];
                    System.out.println("***** SUPPORT GENERICS[" + i + "] = " + gt);


                    if (gt.isCompatibleWith(args[i])) {
                        System.out.println("***** SUPPORT isCompatibleWith[" + i + "]  !!!!!!!!!!!!! ");

                    } else {
                        System.out.println("***** SUPPORT NOT isCompatibleWith[" + i + "]  ");

                    }
                } else {
                    System.out.println("***** SUPPORT NULL NULL GENERICS  [" + i + "] = ");
                }
            }
        }

        for (int i = 0; i < params.length; i++) {
            if (!isAssignableTo(args[i], params[i].getType())) {
                System.out.println("***** allParametersAndArgumentsMatch i=" + i + "; param.name = " + params[i].getName() + "; args[i]=" + args[i].getName());
                return -1;
            } else {
                if (!params[i].getType().equals(args[i])) {
                    dist++;
                }
            }
        }
        System.out.println("***** allParametersAndArgumentsMatch dist=" + dist);

        return dist;
    }

    /**
     * Checks that excess arguments match the vararg signature parameter.
     * @param params
     * @param args
     * @return -1 if no match, 0 if all arguments matches the vararg type and >0 if one or more vararg argument is
     * assignable to the vararg type, but still not an exact match
     */
    static int excessArgumentsMatchesVargsParameter(Parameter[] params, ClassNode[] args) {
        // we already know parameter length is bigger zero and last is a vargs
        // the excess arguments are all put in an array for the vargs call
        // so check against the component type
        int dist = 0;
        ClassNode vargsBase = params[params.length - 1].getType().getComponentType();
        for (int i = params.length; i < args.length; i++) {
            if (!isAssignableTo(args[i], vargsBase)) {
                return -1;
            } else if (!args[i].equals(vargsBase)) {
                dist++;
            }
        }
        return dist;
    }

    /**
     * Checks if the last argument matches the vararg type.
     * @param params
     * @param args
     * @return -1 if no match, 0 if the last argument is exactly the vararg type and 1 if of an assignable type
     */
    static int lastArgMatchesVarg(Parameter[] params, ClassNode... args) {
        if (!isVargs(params)) {
            return -1;
        }
        // case length ==0 handled already
        // we have now two cases,
        // the argument is wrapped in the vargs array or
        // the argument is an array that can be used for the vargs part directly
        // we test only the wrapping part, since the non wrapping is done already
        ClassNode ptype = params[params.length - 1].getType().getComponentType();
        ClassNode arg = args[args.length - 1];
        if (isNumberType(ptype) && isNumberType(arg) && !ptype.equals(arg)) {
            return -1;
        }
        return isAssignableTo(arg, ptype) ? (ptype.equals(arg) ? 0 : 1) : -1;
    }

    /**
     * Checks if a class node is assignable to another. This is used for example in
     * assignment checks where you want to verify that the assignment is valid.
     * @param type
     * @param toBeAssignedTo
     * @return
     */
    static boolean isAssignableTo(ClassNode type, ClassNode toBeAssignedTo) {
        if (toBeAssignedTo.redirect() == STRING_TYPE && type.redirect() == GSTRING_TYPE) {
            return true;
        }
        if (isPrimitiveType(toBeAssignedTo)) {
            toBeAssignedTo = getWrapper(toBeAssignedTo);
        }
        if (isPrimitiveType(type)) {
            type = getWrapper(type);
        }
        if (type.isArray() && toBeAssignedTo.isArray()) {
            return type.getComponentType().equals(toBeAssignedTo.getComponentType());
        }
        if (implementsInterfaceOrIsSubclassOf(type, toBeAssignedTo)) {
            System.out.println("***** 1 StaticTypeCheckingSupportMy.isAssignableTo " + "; type.name = " + type.getName() + "; type =" + type + "; toBeAssignedTo.type.name=" + toBeAssignedTo.getName() + "; toBeAssignedTo.type=" + toBeAssignedTo);
            if (OBJECT_TYPE.equals(toBeAssignedTo)) {
                return true;
            }
            if (toBeAssignedTo.isUsingGenerics()) {
                System.out.println("***** 2 StaticTypeCheckingSupportMy.toBeAssignedTo.isUsingGenerics() toBeAssignedTo.isGenericsPlaceHolder()=" + toBeAssignedTo.isGenericsPlaceHolder());
                if (toBeAssignedTo.isGenericsPlaceHolder() && toBeAssignedTo.getGenericsTypes() != null) {
                    boolean b = false;
                    GenericsType gt = toBeAssignedTo.getGenericsTypes()[0];
                    b = gt.isCompatibleWith(type);
                    System.out.println("***** 3 StaticTypeCheckingSupportMy.toBeAssignedTo.getGenericsTypes()[0].gt.isCompatibleWith=" + b);
                    return b;
                }
                // perform additional check on generics
                // ? extends toBeAssignedTo
                GenericsType gt = GenericsUtils.buildWildcardType(toBeAssignedTo);
                System.out.println("***** 3 StaticTypeCheckingSupportMy.toBeAssignedTo.isUsingGenerics() gt.getType()=" + gt.getType());

                return gt.isCompatibleWith(type);
            }
            System.out.println("***** StaticTypeCheckingSupportMy.toBeAssignedTo = TRUE !!!!!!!!!!");
            return true;
        } else {
            return false;
        }
    }

    static boolean isVargs(Parameter[] params) {
        if (params.length == 0) {
            return false;
        }
        if (params[params.length - 1].getType().isArray()) {
            return true;
        }
        return false;
    }

    static boolean isCompareToBoolean(int op) {
        return op == COMPARE_GREATER_THAN
                || op == COMPARE_GREATER_THAN_EQUAL
                || op == COMPARE_LESS_THAN
                || op == COMPARE_LESS_THAN_EQUAL;
    }

    static boolean isArrayOp(int op) {
        return op == LEFT_SQUARE_BRACKET;
    }

    static boolean isBoolIntrinsicOp(int op) {
        return op == LOGICAL_AND || op == LOGICAL_OR
                || op == MATCH_REGEX || op == KEYWORD_INSTANCEOF;
    }

    static boolean isPowerOperator(int op) {
        return op == POWER || op == POWER_EQUAL;
    }

    static String getOperationName(int op) {
        switch (op) {
            case COMPARE_EQUAL:
            case COMPARE_NOT_EQUAL:
                // this is only correct in this context here, normally
                // we would have to compile against compareTo if available
                // but since we don't compile here, this one is enough
                return "equals";

            case COMPARE_TO:
            case COMPARE_GREATER_THAN:
            case COMPARE_GREATER_THAN_EQUAL:
            case COMPARE_LESS_THAN:
            case COMPARE_LESS_THAN_EQUAL:
                return "compareTo";

            case BITWISE_AND:
            case BITWISE_AND_EQUAL:
                return "and";

            case BITWISE_OR:
            case BITWISE_OR_EQUAL:
                return "or";

            case BITWISE_XOR:
            case BITWISE_XOR_EQUAL:
                return "xor";

            case PLUS:
            case PLUS_EQUAL:
                return "plus";

            case MINUS:
            case MINUS_EQUAL:
                return "minus";

            case MULTIPLY:
            case MULTIPLY_EQUAL:
                return "multiply";

            case DIVIDE:
            case DIVIDE_EQUAL:
                return "div";

            case INTDIV:
            case INTDIV_EQUAL:
                return "intdiv";

            case MOD:
            case MOD_EQUAL:
                return "mod";

            case POWER:
            case POWER_EQUAL:
                return "power";

            case LEFT_SHIFT:
            case LEFT_SHIFT_EQUAL:
                return "leftShift";

            case RIGHT_SHIFT:
            case RIGHT_SHIFT_EQUAL:
                return "rightShift";

            case RIGHT_SHIFT_UNSIGNED:
            case RIGHT_SHIFT_UNSIGNED_EQUAL:
                return "rightShiftUnsigned";

            case KEYWORD_IN:
                return "isCase";

            default:
                return null;
        }
    }

    static boolean isShiftOperation(String name) {
        return "leftShift".equals(name) || "rightShift".equals(name) || "rightShiftUnsigned".equals(name);
    }

    /**
     * Returns true for operations that are of the class, that given a common type class for left and right, the
     * operation "left op right" will have a result in the same type class In Groovy on numbers that is +,-,* as well as
     * their variants with equals.
     */
    static boolean isOperationInGroup(int op) {
        switch (op) {
            case PLUS:
            case PLUS_EQUAL:
            case MINUS:
            case MINUS_EQUAL:
            case MULTIPLY:
            case MULTIPLY_EQUAL:
                return true;
            default:
                return false;
        }
    }

    static boolean isBitOperator(int op) {
        switch (op) {
            case BITWISE_OR_EQUAL:
            case BITWISE_OR:
            case BITWISE_AND_EQUAL:
            case BITWISE_AND:
            case BITWISE_XOR_EQUAL:
            case BITWISE_XOR:
                return true;
            default:
                return false;
        }
    }

    static boolean isAssignment(int op) {
        switch (op) {
            case ASSIGN:
            case LOGICAL_OR_EQUAL:
            case LOGICAL_AND_EQUAL:
            case PLUS_EQUAL:
            case MINUS_EQUAL:
            case MULTIPLY_EQUAL:
            case DIVIDE_EQUAL:
            case INTDIV_EQUAL:
            case MOD_EQUAL:
            case POWER_EQUAL:
            case LEFT_SHIFT_EQUAL:
            case RIGHT_SHIFT_EQUAL:
            case RIGHT_SHIFT_UNSIGNED_EQUAL:
            case BITWISE_OR_EQUAL:
            case BITWISE_AND_EQUAL:
            case BITWISE_XOR_EQUAL:
                return true;
            default:
                return false;
        }
    }

    /**
     * Returns true or false depending on whether the right classnode can be assigned to the left classnode. This method
     * should not add errors by itself: we let the caller decide what to do if an incompatible assignment is found.
     *
     * @param left  the class to be assigned to
     * @param right the assignee class
     * @return false if types are incompatible
     */
    public static boolean checkCompatibleAssignmentTypes(ClassNode left, ClassNode right) {
        return checkCompatibleAssignmentTypes(left, right, null);
    }

    public static boolean checkCompatibleAssignmentTypes(ClassNode left, ClassNode right, Expression rightExpression) {
        ClassNode leftRedirect = left.redirect();
        ClassNode rightRedirect = right.redirect();

        if (right == VOID_TYPE || right == void_WRAPPER_TYPE) {
            return left == VOID_TYPE || left == void_WRAPPER_TYPE;
        }

        // if rightExpression is null and leftExpression is not a primitive type, it's ok
        boolean rightExpressionIsNull = rightExpression instanceof ConstantExpression && ((ConstantExpression) rightExpression).getValue() == null;
        if (rightExpressionIsNull && !isPrimitiveType(left)) {
            return true;
        }

        // on an assignment everything that can be done by a GroovyCast is allowed

        // anything can be assigned to an Object, String, boolean, Boolean
        // or Class typed variable
        if (leftRedirect == OBJECT_TYPE
                || leftRedirect == STRING_TYPE
                || leftRedirect == boolean_TYPE
                || leftRedirect == Boolean_TYPE
                || leftRedirect == CLASS_Type) {
            return true;
        }

        // char as left expression
        if (leftRedirect == char_TYPE && rightRedirect == STRING_TYPE) {
            if (rightExpression != null && rightExpression instanceof ConstantExpression) {
                String value = rightExpression.getText();
                return value.length() == 1;
            }
        }
        if (leftRedirect == Character_TYPE && (rightRedirect == STRING_TYPE || rightExpressionIsNull)) {
            return rightExpressionIsNull || (rightExpression instanceof ConstantExpression && rightExpression.getText().length() == 1);
        }

        // if left is Enum and right is String or GString we do valueOf
        if (leftRedirect.isDerivedFrom(Enum_Type)
                && (rightRedirect == GSTRING_TYPE || rightRedirect == STRING_TYPE)) {
            return true;
        }

        // if right is array, map or collection we try invoking the
        // constructor
        if (rightRedirect.implementsInterface(MAP_TYPE)
                || rightRedirect.implementsInterface(Collection_TYPE)
                || rightRedirect.equals(MAP_TYPE)
                || rightRedirect.equals(Collection_TYPE)
                || rightRedirect.isArray()) {
            //TODO: in case of the array we could maybe make a partial check
            if (leftRedirect.isArray() && rightRedirect.isArray()) {
                return checkCompatibleAssignmentTypes(leftRedirect.getComponentType(), rightRedirect.getComponentType());
            }
            return true;
        }

        // simple check on being subclass
        if (right.isDerivedFrom(left) || (left.isInterface() && right.implementsInterface(left))) {
            return true;
        }

        // if left and right are primitives or numbers allow
        if (isPrimitiveType(leftRedirect) && isPrimitiveType(rightRedirect)) {
            return true;
        }
        if (isNumberType(leftRedirect) && isNumberType(rightRedirect)) {
            return true;
        }

        return false;
    }

    static boolean checkPossibleLooseOfPrecision(ClassNode left, ClassNode right, Expression rightExpr) {
        if (left == right || left.equals(right)) {
            return false; // identical types
        }
        int leftIndex = NUMBER_TYPES.get(left);
        int rightIndex = NUMBER_TYPES.get(right);
        if (leftIndex >= rightIndex) {
            return false;
        }
        // here we must check if the right number is short enough to fit in the left type
        if (rightExpr instanceof ConstantExpression) {
            Object value = ((ConstantExpression) rightExpr).getValue();
            if (!(value instanceof Number)) {
                return true;
            }
            Number number = (Number) value;
            switch (leftIndex) {
                case 0: { // byte
                    byte val = number.byteValue();
                    if (number instanceof Short) {
                        return !Short.valueOf(val).equals(number);
                    }
                    if (number instanceof Integer) {
                        return !Integer.valueOf(val).equals(number);
                    }
                    if (number instanceof Long) {
                        return !Long.valueOf(val).equals(number);
                    }
                    if (number instanceof Float) {
                        return !Float.valueOf(val).equals(number);
                    }
                    return !Double.valueOf(val).equals(number);
                }
                case 1: { // short
                    short val = number.shortValue();
                    if (number instanceof Integer) {
                        return !Integer.valueOf(val).equals(number);
                    }
                    if (number instanceof Long) {
                        return !Long.valueOf(val).equals(number);
                    }
                    if (number instanceof Float) {
                        return !Float.valueOf(val).equals(number);
                    }
                    return !Double.valueOf(val).equals(number);
                }
                case 2: { // integer
                    int val = number.intValue();
                    if (number instanceof Long) {
                        return !Long.valueOf(val).equals(number);
                    }
                    if (number instanceof Float) {
                        return !Float.valueOf(val).equals(number);
                    }
                    return !Double.valueOf(val).equals(number);
                }
                case 3: { // long
                    long val = number.longValue();
                    if (number instanceof Float) {
                        return !Float.valueOf(val).equals(number);
                    }
                    return !Double.valueOf(val).equals(number);
                }
                case 4: { // float
                    float val = number.floatValue();
                    return !Double.valueOf(val).equals(number);
                }
                default: // double
                    return false; // no possible loose here
            }
        }
        return true; // possible loose of precision
    }

    static String toMethodParametersString(String methodName, ClassNode... parameters) {
        StringBuilder sb = new StringBuilder();
        sb.append(methodName).append("(");
        if (parameters != null) {
            for (int i = 0, parametersLength = parameters.length; i < parametersLength; i++) {
                final ClassNode parameter = parameters[i];
                sb.append(parameter.toString(false));
                if (i < parametersLength - 1) {
                    sb.append(", ");
                }
            }
        }
        sb.append(")");
        return sb.toString();
    }

    static boolean implementsInterfaceOrIsSubclassOf(ClassNode type, ClassNode superOrInterface) {
        return type.equals(superOrInterface) || type.isDerivedFrom(superOrInterface) || type.implementsInterface(superOrInterface);
    }
}
