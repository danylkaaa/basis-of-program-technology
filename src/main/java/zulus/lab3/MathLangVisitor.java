package zulus.lab3;

import org.antlr.v4.runtime.misc.ParseCancellationException;
import zulus.lab1.Matrix;
import zulus.lab3.grammar.MathLangBaseVisitor;
import zulus.lab3.grammar.MathLangParser;

import java.util.*;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public class MathLangVisitor extends MathLangBaseVisitor<Variable> {
    private Map<String, Variable> memory;

    public MathLangVisitor(Map<String, Variable> storage) {
        if (storage == null) {
            throw new IllegalArgumentException("Argument 'storage' is null");
        }
        this.memory = storage;
    }

    // VARIABLE=expression
    @Override
    public Variable visitAssign(MathLangParser.AssignContext ctx) {
        String variableName = ctx.VAR().getText();
        Variable value = visit(ctx.expression());
        memory.put(variableName, value);
        return value;
    }

    // expression | assign
    @Override
    public Variable visitPrint(MathLangParser.PrintContext ctx) {
        return new Variable<>(visit(ctx.expression()).getValue().toString(), String.class);
    }


    @Override
    public Variable visitScientific(MathLangParser.ScientificContext ctx) {
        try {
            return new Variable<>(Double.parseDouble(ctx.SCIENTIFIC_NUMBER().getText()), Double.class);
        } catch (NumberFormatException exc) {
            throw new ParseCancellationException(String.format("Invalid number format '%s'", ctx.SCIENTIFIC_NUMBER().getText()));
        }
    }

    // VARIABLE | NUMBER | matrix
    @Override
    public Variable visitAtom(MathLangParser.AtomContext ctx) {
        if (ctx.variable() != null) {
            String varID = ctx.variable().getText();
            try {
                Variable v = memory.get(varID);
                if (v == null) {
                    throw new NoSuchElementException();
                } else {
                    return v;
                }
            } catch (NoSuchElementException exception) {
                throw new ParseCancellationException(String.format("Variable '%s' is undefined", varID));
            }
        } else if (ctx.scientific() != null) {
            return visit(ctx.scientific());
        } else if (ctx.expression() != null) {
            return visit(ctx.expression());
        } else {
            throw new ParseCancellationException(String.format("Cannot recognize type of atom"));
        }
    }

    // +atom |- atom | atom
    @Override
    public Variable visitInvertSignedAtom(MathLangParser.InvertSignedAtomContext ctx) {
        return visit(ctx.signedAtom()).setSign();
    }

    @Override
    public Variable visitPlainFactor(MathLangParser.PlainFactorContext ctx) {
        return visit(ctx.factor());
    }

    @Override
    public Variable visitPlainAtom(MathLangParser.PlainAtomContext ctx) {
        if (ctx.atom() != null) {
            return visit(ctx.atom());
        } else {
            return visit(ctx.signedAtom());
        }
    }


    // expression + expression
    @Override
    public Variable visitSumExpression(MathLangParser.SumExpressionContext ctx) {
        Variable left = visit(ctx.expression(0));
        Variable right = visit(ctx.expression(1));
        if (left == null || right == null) {
            throw new ParseCancellationException("Invalid operation form. It's a binary operation");
        }
        if (!left.getValueType().equals(right.getValueType())) {
            throw new ParseCancellationException(String.format("SUM cannot be applied to operands of type %s and %s", left.getValueType().getName(), right.getValueType().getName()));
        }
        try {
            if (left.getValueType().equals(Double.class)) {
                Double leftD = Converter.convertToDouble(left);
                Double rightD = Converter.convertToDouble(right);
                return new Variable<>(leftD + rightD, Double.class);
            } else if (left.getValueType().equals(Matrix.class)) {
                Matrix leftM = Converter.convertToMatrix(left);
                Matrix rightM = Converter.convertToMatrix(right);
                return new Variable<>(leftM.add(rightM), Matrix.class);
            }
        } catch (IllegalArgumentException | ConvertationException exc) {
            throw new ParseCancellationException("SUM cannot be applied:" + exc.getMessage());
        }
        throw new ParseCancellationException(String.format("SUM cannot be applied to operands of type %s and %s", left.getValueType().getName(), right.getValueType().getName()));
    }

    // expression - expression
    @Override
    public Variable visitSubtractExpression(MathLangParser.SubtractExpressionContext ctx) {
        Variable left = visit(ctx.expression(0));
        Variable right = visit(ctx.expression(1));
        if (left == null || right == null) {
            throw new ParseCancellationException("Invalid operation form. It's a binary operation");
        }
        if (!left.getValueType().equals(right.getValueType())) {
            throw new ParseCancellationException(String.format("SUBTRACT cannot be applied to operands of type %s and %s", left.getValueType().getName(), right.getValueType().getName()));
        }
        try {
            if (left.getValueType().equals(Double.class)) {
                Double leftD = Converter.convertToDouble(left);
                Double rightD = Converter.convertToDouble(right);
                return new Variable<>(leftD - rightD, Double.class);
            } else if (left.getValueType().equals(Matrix.class)) {
                Matrix leftM = Converter.convertToMatrix(left);
                Matrix rightM = Converter.convertToMatrix(right);
                return new Variable<>(leftM.add(rightM.multiply(-1)), Matrix.class);
            }
        } catch (ConvertationException | IllegalArgumentException exc) {
            throw new ParseCancellationException("SUBTRACT cannot be applied:" + exc.getMessage());
        }
        throw new ParseCancellationException(String.format("SUBTRACT cannot be applied to operands of type %s and %s", left.getValueType().getName(), right.getValueType().getName()));
    }

    private Matrix getMatrixFromLists(List<Variable> members) {
        List first = (List) members.get(0).getValue();
        Stream<List> arrays = members.stream().map(x -> (List) x.getValue());
        if (arrays.anyMatch(x -> x.size() != first.size())) {
            throw new ParseCancellationException("Matrix definition includes lists with different length");
        }
        return new Matrix(arrays.map(x -> x.stream().toArray(Double[]::new)).toArray(Double[][]::new));
    }

    @Override
    public Variable visitArray(MathLangParser.ArrayContext ctx) {
        // collect all members
        Stream<Variable> members = ctx.expression().stream().map(this::visit);
        if (members.count() == 0) return new Variable<>(new Matrix(0, 0), Matrix.class);
        if (members.anyMatch(x -> !x.getValueType().equals(Double.class))) {
            throw new ParseCancellationException("Array can only contain doubles");
        } else {
            return new Variable<>(
                    members.mapToDouble(x -> (Double) x.getValue()).boxed().collect(Collectors.toList()),
                    List.class);
        }
    }

    // [...]
    @Override
    public Variable visitMatrix(MathLangParser.MatrixContext ctx) {
        // collect all members
        List<Variable> members = ctx.expression().stream().map(this::visit).collect(Collectors.toList());
        if (members.size() == 0) return new Variable<>(new Matrix(0, 0), Matrix.class);
        Variable first = members.get(0);
        // check, all members has the same type
        if (members.stream().anyMatch(x -> !x.getValueType().equals(first.getValueType()))) {
            throw new ParseCancellationException("Matrix definition includes non-identical members");
        } else if (first.getValueType().equals(Double.class)) {
            // check all members are Double
            double[] source = members.stream().mapToDouble(x -> (Double) x.getValue()).toArray();
            return new Variable<>(new Matrix(new double[][]{source}), Matrix.class);
        } else if (first.getValueType().isAssignableFrom(List.class)) {
            // check all members are Lists
            return new Variable<>(getMatrixFromLists(members), Matrix.class);
        } else {
            throw new ParseCancellationException(String.format("Matrix definition includes unauthorized types: %s", first.getValueType().getName()));
        }
    }

    @Override
    public Variable visitDeterminantExpression(MathLangParser.DeterminantExpressionContext ctx) {
        Variable arg = visit(ctx.expression());
        try {
            return new Variable<>(Converter.convertToMatrix(arg).determinant(), Double.class);
        } catch (ConvertationException exc) {
            throw new ParseCancellationException("Cannot calculate determinant of not a matrix member");
        } catch (IllegalArgumentException exc) {
            throw new ParseCancellationException(exc.getMessage());
        }
    }
}
