/*-******************************************************************************
 * Copyright (c) 2014 Iwao AVE!.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *    Iwao AVE! - initial API and implementation and/or initial documentation
 *******************************************************************************/

package net.harawata.mybatipse.mybatis;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.eclipse.core.runtime.NullProgressMonitor;
import org.eclipse.jdt.core.ICompilationUnit;
import org.eclipse.jdt.core.IMethod;
import org.eclipse.jdt.core.dom.AST;
import org.eclipse.jdt.core.dom.ASTNode;
import org.eclipse.jdt.core.dom.ASTParser;
import org.eclipse.jdt.core.dom.ASTVisitor;
import org.eclipse.jdt.core.dom.AnonymousClassDeclaration;
import org.eclipse.jdt.core.dom.ArrayInitializer;
import org.eclipse.jdt.core.dom.CompilationUnit;
import org.eclipse.jdt.core.dom.Expression;
import org.eclipse.jdt.core.dom.IAnnotationBinding;
import org.eclipse.jdt.core.dom.IMemberValuePairBinding;
import org.eclipse.jdt.core.dom.IVariableBinding;
import org.eclipse.jdt.core.dom.InfixExpression;
import org.eclipse.jdt.core.dom.MethodDeclaration;
import org.eclipse.jdt.core.dom.SingleMemberAnnotation;
import org.eclipse.jdt.core.dom.SingleVariableDeclaration;
import org.eclipse.jdt.core.dom.StringLiteral;
import org.eclipse.jdt.core.dom.TypeDeclaration;

/**
 * @author Iwao AVE!
 */
public class JavaMapperUtil
{
	public static final String TYPE_ROW_BOUNDS = "org.apache.ibatis.session.RowBounds";

	private static final String ANNOTATION_PARAM = "org.apache.ibatis.annotations.Param";

	public static Map<String, String> getMethodParameters(ASTNode node, IMethod method)
	{
		Map<String, String> results = new HashMap<String, String>();

		MapperMethod mapperMethod = getMapperMethod(node, method);
		if (mapperMethod == null)
			return results;

		@SuppressWarnings("unchecked")
		List<SingleVariableDeclaration> params = mapperMethod.parameters();
		for (int i = 0; i < params.size(); i++)
		{
			IVariableBinding paramBinding = params.get(i).resolveBinding();
			String paramFqn = paramBinding.getType().getQualifiedName();
			if (TYPE_ROW_BOUNDS.equals(paramFqn))
				continue;
			IAnnotationBinding[] annotations = paramBinding.getAnnotations();
			for (IAnnotationBinding annotation : annotations)
			{
				if (ANNOTATION_PARAM.equals(annotation.getAnnotationType().getQualifiedName()))
				{
					IMemberValuePairBinding[] valuePairs = annotation.getAllMemberValuePairs();
					if (valuePairs.length == 1)
					{
						IMemberValuePairBinding valuePairBinding = valuePairs[0];
						String paramValue = (String)valuePairBinding.getValue();
						results.put(paramValue, paramFqn);
					}
				}
			}
			results.put("param" + (i + 1), paramFqn); //$NON-NLS-1$
		}
		return results;
	}

	public static MapperMethod getMapperMethod(ASTNode node, IMethod method)
	{
		if (method == null)
			return null;

		StatementVisitor visitor = new StatementVisitor(method);
		node.accept(visitor);
		return visitor.getMapperMethod();
	}

	private static class StatementVisitor extends ASTVisitor
	{
		private IMethod targetMethod;

		private MapperMethod mapperMethod;

		private int nestLevel;

		public StatementVisitor(IMethod targetMethod)
		{
			this.targetMethod = targetMethod;
		}

		@Override
		public boolean visit(TypeDeclaration node)
		{
			String targetType = targetMethod.getDeclaringType()
				.getFullyQualifiedName()
				.replace('$', '.');
			String currentType = node.resolveBinding().getQualifiedName();
			if (targetType.equals(currentType))
				nestLevel = 1;
			else if (nestLevel > 0)
				nestLevel++;

			return true;
		}

		@Override
		public boolean visit(AnonymousClassDeclaration node)
		{
			return false;
		}

		@Override
		public boolean visit(MethodDeclaration node)
		{
			if (nestLevel != 1)
				return false;
			if (targetMethod.getElementName().equals(node.getName().getFullyQualifiedName()))
			{
				IMethod method = (IMethod)node.resolveBinding().getJavaElement();
				if (targetMethod.isSimilar(method))
				{
					mapperMethod = new MapperMethod();
					mapperMethod.setMethodDeclaration(node);
					return true;
				}
			}
			return false;
		}

		@Override
		public boolean visit(SingleMemberAnnotation node)
		{
			if (nestLevel != 1)
				return false;
			String typeFqn = node.resolveTypeBinding().getQualifiedName();
			if ("org.apache.ibatis.annotations.Select".equals(typeFqn)
				|| "org.apache.ibatis.annotations.Update".equals(typeFqn)
				|| "org.apache.ibatis.annotations.Insert".equals(typeFqn)
				|| "org.apache.ibatis.annotations.Delete".equals(typeFqn))
			{
				Expression value = node.getValue();
				int valueType = value.getNodeType();
				if (valueType == ASTNode.STRING_LITERAL)
				{
					mapperMethod.setStatement(((StringLiteral)value).getLiteralValue());
				}
				else if (valueType == ASTNode.ARRAY_INITIALIZER)
				{
					StringBuilder buffer = new StringBuilder();
					@SuppressWarnings("unchecked")
					List<Expression> expressions = (List<Expression>)((ArrayInitializer)value).expressions();
					for (Expression expression : expressions)
					{
						int expressionType = expression.getNodeType();
						if (expressionType == ASTNode.STRING_LITERAL)
						{
							if (buffer.length() > 0)
								buffer.append(' ');
							buffer.append(((StringLiteral)expression).getLiteralValue());
						}
						else if (expressionType == ASTNode.INFIX_EXPRESSION)
						{
							buffer.append(parseInfixExpression((InfixExpression)expression));
						}
					}
					mapperMethod.setStatement(buffer.toString());
				}
				else if (valueType == ASTNode.INFIX_EXPRESSION)
				{
					mapperMethod.setStatement(parseInfixExpression((InfixExpression)value));
				}
			}
			return false;
		}

		private String parseInfixExpression(InfixExpression expression)
		{
			// will implement if someone really wants it...
			return expression.toString();
		}

		@Override
		public void endVisit(TypeDeclaration node)
		{
			nestLevel--;
		}

		public MapperMethod getMapperMethod()
		{
			return mapperMethod;
		}
	}

	public static CompilationUnit getAstNode(ICompilationUnit compilationUnit)
	{
		ASTParser parser = ASTParser.newParser(AST.JLS4);
		parser.setSource(compilationUnit);
		parser.setKind(ASTParser.K_COMPILATION_UNIT);
		parser.setResolveBindings(true);
		return (CompilationUnit)parser.createAST(new NullProgressMonitor());
	}
}
