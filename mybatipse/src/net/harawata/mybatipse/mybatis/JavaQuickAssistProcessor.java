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

import java.util.ArrayList;
import java.util.List;

import net.harawata.mybatipse.Activator;

import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.NullProgressMonitor;
import org.eclipse.core.runtime.Status;
import org.eclipse.jdt.core.IAnnotation;
import org.eclipse.jdt.core.ICompilationUnit;
import org.eclipse.jdt.core.IJavaElement;
import org.eclipse.jdt.core.IMethod;
import org.eclipse.jdt.core.IType;
import org.eclipse.jdt.core.JavaModelException;
import org.eclipse.jdt.core.dom.AST;
import org.eclipse.jdt.core.dom.ASTNode;
import org.eclipse.jdt.core.dom.ASTParser;
import org.eclipse.jdt.core.dom.ASTVisitor;
import org.eclipse.jdt.core.dom.Annotation;
import org.eclipse.jdt.core.dom.AnonymousClassDeclaration;
import org.eclipse.jdt.core.dom.ArrayInitializer;
import org.eclipse.jdt.core.dom.CompilationUnit;
import org.eclipse.jdt.core.dom.Expression;
import org.eclipse.jdt.core.dom.IExtendedModifier;
import org.eclipse.jdt.core.dom.InfixExpression;
import org.eclipse.jdt.core.dom.MethodDeclaration;
import org.eclipse.jdt.core.dom.SingleMemberAnnotation;
import org.eclipse.jdt.core.dom.SingleVariableDeclaration;
import org.eclipse.jdt.core.dom.StringLiteral;
import org.eclipse.jdt.core.dom.TypeDeclaration;
import org.eclipse.jdt.ui.text.java.IInvocationContext;
import org.eclipse.jdt.ui.text.java.IJavaCompletionProposal;
import org.eclipse.jdt.ui.text.java.IProblemLocation;
import org.eclipse.jdt.ui.text.java.IQuickAssistProcessor;
import org.eclipse.jface.text.BadLocationException;
import org.eclipse.jface.text.IDocument;
import org.eclipse.jface.text.contentassist.IContextInformation;
import org.eclipse.swt.dnd.Clipboard;
import org.eclipse.swt.dnd.TextTransfer;
import org.eclipse.swt.dnd.Transfer;
import org.eclipse.swt.graphics.Image;
import org.eclipse.swt.graphics.Point;
import org.eclipse.swt.widgets.Display;
import org.eclipse.text.edits.MalformedTreeException;
import org.eclipse.text.edits.TextEdit;

/**
 * @author Iwao AVE!
 */
public class JavaQuickAssistProcessor implements IQuickAssistProcessor
{
	public JavaQuickAssistProcessor()
	{
		super();
	}

	@Override
	public boolean hasAssists(IInvocationContext context) throws CoreException
	{
		return false;
	}

	@Override
	public IJavaCompletionProposal[] getAssists(IInvocationContext context,
		IProblemLocation[] locations) throws CoreException
	{
		ICompilationUnit compilationUnit = context.getCompilationUnit();
		IType primaryType = compilationUnit.findPrimaryType();
		if (primaryType == null || !primaryType.isInterface())
			; // return null;

		IJavaElement[] elements = compilationUnit.codeSelect(context.getSelectionOffset(),
			context.getSelectionLength());
		for (IJavaElement element : elements)
		{
			if (element.getElementType() == IJavaElement.METHOD)
			{
				IMethod method = (IMethod)element;
				if (!method.getDeclaringType().isInterface())
					return null;

				final String statementAnnotation = getStatementAnnotation(method);
				if (method.getParameters().length == 0 && statementAnnotation == null)
					return null;

				CompilationUnit astNode = getAstNode(compilationUnit);
				astNode.recordModifications();
				final MapperMethod mapperMethod = getMapperMethod(astNode, method);
				if (mapperMethod == null)
					return null;

				List<IJavaCompletionProposal> proposals = new ArrayList<IJavaCompletionProposal>();

				if (method.getParameters().length > 0)
				{
					proposals.add(new QuickAssistCompletionProposal("Add @Param to parameters")
					{
						private CompilationUnit astNode;

						private MapperMethod method;

						@SuppressWarnings("unchecked")
						@Override
						public void apply(IDocument document)
						{
							List<SingleVariableDeclaration> params = method.parameters();
							for (SingleVariableDeclaration param : params)
							{
								List<IExtendedModifier> modifiers = param.modifiers();
								if (!hasParamAnnotation(modifiers))
								{
									if (JavaMapperUtil.TYPE_ROW_BOUNDS.equals(param.resolveBinding()
										.getType()
										.getQualifiedName()))
										continue;
									AST ast = param.getAST();
									SingleMemberAnnotation annotation = ast.newSingleMemberAnnotation();
									annotation.setTypeName(ast.newName("Param"));
									StringLiteral paramValue = ast.newStringLiteral();
									paramValue.setLiteralValue(param.getName().getFullyQualifiedName());
									annotation.setValue(paramValue);
									param.modifiers().add(annotation);
								}
							}
							TextEdit textEdit = astNode.rewrite(document, null);
							try
							{
								textEdit.apply(document);
							}
							catch (MalformedTreeException e)
							{
								Activator.log(Status.ERROR, e.getMessage(), e);
							}
							catch (BadLocationException e)
							{
								Activator.log(Status.ERROR, e.getMessage(), e);
							}
						}

						private boolean hasParamAnnotation(List<IExtendedModifier> modifiers)
						{
							for (IExtendedModifier modifier : modifiers)
							{
								if (modifier.isAnnotation()
									&& "Param".equals(((Annotation)modifier).getTypeName()
										.getFullyQualifiedName()))
								{
									return true;
								}
							}
							return false;
						}

						private QuickAssistCompletionProposal init(CompilationUnit astNode,
							MapperMethod method)
						{
							this.astNode = astNode;
							this.method = method;
							return this;
						}
					}.init(astNode, mapperMethod));
				}

				if (mapperMethod.getStatement() != null)
				{
					proposals.add(new QuickAssistCompletionProposal("Copy @" + statementAnnotation
						+ " statement to clipboard")
					{
						@Override
						public void apply(IDocument document)
						{
							Clipboard clipboard = new Clipboard(Display.getCurrent());
							clipboard.setContents(new Object[]{
								mapperMethod.getStatement()
							}, new Transfer[]{
								TextTransfer.getInstance()
							});
						}
					});
				}

				return proposals.toArray(new IJavaCompletionProposal[proposals.size()]);
			}
		}
		return null;
	}

	private String getStatementAnnotation(IMethod method) throws JavaModelException
	{
		IAnnotation[] annotations = method.getAnnotations();
		for (IAnnotation annotation : annotations)
		{
			String name = annotation.getElementName();
			if ("Select".equals(name) || "Insert".equals(name) || "Update".equals(name)
				|| "Delete".equals(name))
				return name;
		}
		return null;
	}

	static abstract class QuickAssistCompletionProposal implements IJavaCompletionProposal
	{
		private String displayString;

		private QuickAssistCompletionProposal(String displayString)
		{
			super();
			this.displayString = displayString;
		}

		@Override
		public Point getSelection(IDocument document)
		{
			return null;
		}

		@Override
		public String getAdditionalProposalInfo()
		{
			return null;
		}

		@Override
		public String getDisplayString()
		{
			return displayString;
		}

		@Override
		public Image getImage()
		{
			return Activator.getIcon();
		}

		@Override
		public IContextInformation getContextInformation()
		{
			return null;
		}

		@Override
		public int getRelevance()
		{
			return 500;
		}
	}

	private CompilationUnit getAstNode(ICompilationUnit compilationUnit)
	{
		ASTParser parser = ASTParser.newParser(AST.JLS4);
		parser.setSource(compilationUnit);
		parser.setKind(ASTParser.K_COMPILATION_UNIT);
		parser.setResolveBindings(true);
		return (CompilationUnit)parser.createAST(new NullProgressMonitor());
	}

	private MapperMethod getMapperMethod(ASTNode node, IMethod method)
	{
		if (method == null)
			return null;

		StatementVisitor visitor = new StatementVisitor(method);
		node.accept(visitor);
		return visitor.getMapperMethod();
	}

	class StatementVisitor extends ASTVisitor
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

	class MapperMethod
	{
		private MethodDeclaration methodDeclaration;

		private String statement;

		public MethodDeclaration getMethodDeclaration()
		{
			return methodDeclaration;
		}

		public void setMethodDeclaration(MethodDeclaration methodDeclaration)
		{
			this.methodDeclaration = methodDeclaration;
		}

		public String getStatement()
		{
			return statement;
		}

		public void setStatement(String statement)
		{
			this.statement = statement;
		}

		@SuppressWarnings("rawtypes")
		public List parameters()
		{
			return this.methodDeclaration.parameters();
		}
	}
}
