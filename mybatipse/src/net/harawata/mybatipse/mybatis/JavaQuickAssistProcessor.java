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

import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

import javax.xml.xpath.XPathExpressionException;

import org.eclipse.core.resources.IFile;
import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.NullProgressMonitor;
import org.eclipse.core.runtime.Status;
import org.eclipse.jdt.core.IAnnotation;
import org.eclipse.jdt.core.ICompilationUnit;
import org.eclipse.jdt.core.IJavaElement;
import org.eclipse.jdt.core.IJavaProject;
import org.eclipse.jdt.core.IMethod;
import org.eclipse.jdt.core.IType;
import org.eclipse.jdt.core.ITypeHierarchy;
import org.eclipse.jdt.core.JavaModelException;
import org.eclipse.jdt.core.dom.AST;
import org.eclipse.jdt.core.dom.ASTNode;
import org.eclipse.jdt.core.dom.ASTParser;
import org.eclipse.jdt.core.dom.ASTVisitor;
import org.eclipse.jdt.core.dom.Annotation;
import org.eclipse.jdt.core.dom.AnonymousClassDeclaration;
import org.eclipse.jdt.core.dom.ArrayInitializer;
import org.eclipse.jdt.core.dom.ArrayType;
import org.eclipse.jdt.core.dom.CompilationUnit;
import org.eclipse.jdt.core.dom.Expression;
import org.eclipse.jdt.core.dom.IExtendedModifier;
import org.eclipse.jdt.core.dom.ITypeBinding;
import org.eclipse.jdt.core.dom.InfixExpression;
import org.eclipse.jdt.core.dom.MethodDeclaration;
import org.eclipse.jdt.core.dom.ParameterizedType;
import org.eclipse.jdt.core.dom.PrimitiveType;
import org.eclipse.jdt.core.dom.SingleMemberAnnotation;
import org.eclipse.jdt.core.dom.SingleVariableDeclaration;
import org.eclipse.jdt.core.dom.StringLiteral;
import org.eclipse.jdt.core.dom.Type;
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
import org.eclipse.wst.sse.core.StructuredModelManager;
import org.eclipse.wst.sse.core.internal.provisional.IStructuredModel;
import org.eclipse.wst.xml.core.internal.provisional.document.IDOMDocument;
import org.eclipse.wst.xml.core.internal.provisional.document.IDOMModel;
import org.eclipse.wst.xml.core.internal.provisional.format.FormatProcessorXML;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.Text;

import net.harawata.mybatipse.Activator;
import net.harawata.mybatipse.MybatipseConstants;
import net.harawata.mybatipse.util.NameUtil;
import net.harawata.mybatipse.util.XpathUtil;

/**
 * @author Iwao AVE!
 */
@SuppressWarnings("restriction")
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
			return null;

		IJavaElement[] elements = compilationUnit.codeSelect(context.getSelectionOffset(),
			context.getSelectionLength());
		for (IJavaElement element : elements)
		{
			if (element.getElementType() == IJavaElement.METHOD)
			{
				IMethod method = (IMethod)element;
				if (!method.getDeclaringType().isInterface())
					return null;

				CompilationUnit astNode = getAstNode(compilationUnit);
				astNode.recordModifications();
				final MapperMethod mapperMethod = getMapperMethod(astNode, method);
				if (mapperMethod == null)
					return null;

				List<IJavaCompletionProposal> proposals = new ArrayList<IJavaCompletionProposal>();

				if (method.getParameters().length > 0)
				{
					proposals.add(
						new AddParamQuickAssist("Add @Param to parameters").init(astNode, mapperMethod));
				}

				if (mapperMethod.getStatement() != null)
				{
					final IAnnotation statementAnnotation = getStatementAnnotation(method);
					final String statementType = statementAnnotation.getElementName();
					try
					{
						final String mapperFqn = primaryType.getFullyQualifiedName();
						IJavaProject project = compilationUnit.getJavaProject();
						IFile mapperFile = MapperNamespaceCache.getInstance().get(project, mapperFqn, null);
						if (mapperFile != null)
						{
							IDOMDocument mapperDocument = MybatipseXmlUtil.getMapperDocument(mapperFile);
							if (mapperDocument != null)
							{
								Node domNode = XpathUtil.xpathNode(mapperDocument,
									"//*[@id='" + method.getElementName() + "']");
								if (domNode == null)
								{
									// only when the element does not exist
									proposals.add(new MoveStatementToXmlQuickAssist("Move @" + statementType
										+ " statement to <" + statementType.toLowerCase() + " />").init(project,
											mapperFile, statementAnnotation, mapperMethod, astNode));
								}
							}
						}
					}
					catch (XPathExpressionException e)
					{
						Activator.log(Status.ERROR, e.getMessage(), e);
					}

					proposals.add(new QuickAssistCompletionProposal(
						"Copy @" + statementType + " statement to clipboard")
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

	private IAnnotation getStatementAnnotation(IMethod method) throws JavaModelException
	{
		IAnnotation[] annotations = method.getAnnotations();
		for (IAnnotation annotation : annotations)
		{
			String name = annotation.getElementName();
			if ("Select".equals(name) || "Insert".equals(name) || "Update".equals(name)
				|| "Delete".equals(name))
				return annotation;
		}
		return null;
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

	private final class MoveStatementToXmlQuickAssist extends QuickAssistCompletionProposal
	{
		private IJavaProject project;

		private IFile mapperFile;

		private IAnnotation statementAnnotation;

		private MapperMethod method;

		private CompilationUnit astNode;

		private MoveStatementToXmlQuickAssist(String displayString)
		{
			super(displayString);
		}

		@Override
		public void apply(IDocument document)
		{
			try
			{
				// TODO: move @Results to <resultMap />
				addXmlStatement();
				deleteStatementAnnotation(document);
			}
			catch (Exception e)
			{
				Activator.log(Status.ERROR, e.getMessage(), e);
			}
		}

		private void deleteStatementAnnotation(IDocument document) throws BadLocationException
		{
			@SuppressWarnings("unchecked")
			List<IExtendedModifier> modifiers = method.getMethodDeclaration().modifiers();
			Iterator<IExtendedModifier> iter = modifiers.iterator();
			while (iter.hasNext())
			{
				IExtendedModifier modifier = (IExtendedModifier)iter.next();
				if (modifier.isAnnotation())
				{
					Annotation annotation = (Annotation)modifier;
					String name = annotation.getTypeName().getFullyQualifiedName();
					if ("Select".equals(name) || "Insert".equals(name) || "Update".equals(name)
						|| "Delete".equals(name) || "ResultMap".equals(name))
					{
						iter.remove();
					}
				}
			}
			TextEdit textEdit = astNode.rewrite(document, null);
			textEdit.apply(document);
		}

		private void addXmlStatement()
			throws IOException, CoreException, UnsupportedEncodingException
		{
			IStructuredModel model = StructuredModelManager.getModelManager()
				.getModelForEdit(mapperFile);
			if (model == null)
			{
				return;
			}
			try
			{
				model.beginRecording(this);
				model.aboutToChangeModel();
				if (model instanceof IDOMModel)
				{
					String delimiter = model.getStructuredDocument().getLineDelimiter();
					IDOMDocument mapperDoc = ((IDOMModel)model).getDocument();
					Element root = mapperDoc.getDocumentElement();
					Element element = createStatementElement(mapperDoc, delimiter);
					root.appendChild(element);
					root.appendChild(mapperDoc.createTextNode(delimiter));
					new FormatProcessorXML().formatNode(element);
				}
			}
			finally
			{
				model.changedModel();
				if (!model.isSharedForEdit() && model.isSaveNeeded())
				{
					model.save();
				}
				model.endRecording(this);
				model.releaseFromEdit();
			}
		}

		private Element createStatementElement(IDOMDocument mapperDoc, String delimiter)
		{
			String statement = statementAnnotation.getElementName().toLowerCase();
			Element element = mapperDoc.createElement(statement);
			MethodDeclaration methodDeclaration = method.getMethodDeclaration();
			element.setAttribute("id", methodDeclaration.getName().toString());
			if (method.isSelect())
			{
				if (method.getResultMap() != null)
				{
					element.setAttribute("resultMap", method.getResultMap());
				}
				else
				{
					Type returnType2 = methodDeclaration.getReturnType2();
					if (returnType2.isPrimitiveType())
					{
						element.setAttribute("resultType", "_" + returnType2.toString());
					}
					else if (returnType2.isArrayType())
					{
						Type componentType = ((ArrayType)returnType2).getElementType();
						if (componentType.isPrimitiveType())
						{
							element.setAttribute("resultType", "_" + returnType2.toString());
						}
						else
						{
							element.setAttribute("resultType",
								NameUtil.stripTypeArguments(componentType.resolveBinding().getQualifiedName()));
						}
					}
					else if (returnType2.isParameterizedType())
					{
						try
						{
							ParameterizedType parameterizedType = (ParameterizedType)returnType2;
							String qualifiedName = parameterizedType.getType()
								.resolveBinding()
								.getQualifiedName();
							IType type = project.findType(NameUtil.stripTypeArguments(qualifiedName));
							ITypeHierarchy supertypeHierarchy = type
								.newSupertypeHierarchy(new NullProgressMonitor());
							if (supertypeHierarchy.contains(project.findType("java.util.Collection")))
							{
								List<String> typeParams = NameUtil.extractTypeParams(qualifiedName);
								if (typeParams.size() == 1)
								{
									element.setAttribute("resultType",
										NameUtil.stripTypeArguments(typeParams.get(0)));
								}
							}
							else if (supertypeHierarchy.contains(project.findType("java.util.Map")))
							{
								if (method.isHasMapKey())
								{
									List<String> typeParams = NameUtil.extractTypeParams(qualifiedName);
									if (typeParams.size() == 2)
									{
										element.setAttribute("resultType",
											NameUtil.stripTypeArguments(typeParams.get(1)));
									}
								}
								else
								{
									element.setAttribute("resultType", type.getFullyQualifiedName());
								}
							}
						}
						catch (JavaModelException e)
						{
							Activator.log(Status.ERROR, e.getMessage(), e);
						}
					}
				}
			}
			Text sqlText = mapperDoc.createTextNode(delimiter + method.getStatement() + delimiter);
			element.appendChild(sqlText);
			return element;
		}

		private MoveStatementToXmlQuickAssist init(IJavaProject project, IFile mapperFile,
			IAnnotation statementAnno, MapperMethod method, CompilationUnit astNode)
		{
			this.project = project;
			this.mapperFile = mapperFile;
			this.statementAnnotation = statementAnno;
			this.method = method;
			this.astNode = astNode;
			return this;
		}
	}

	private final class AddParamQuickAssist extends QuickAssistCompletionProposal
	{
		private CompilationUnit astNode;

		private MapperMethod method;

		private AddParamQuickAssist(String displayString)
		{
			super(displayString);
		}

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
					if (MybatipseConstants.TYPE_ROW_BOUNDS
						.equals(param.resolveBinding().getType().getQualifiedName()))
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
					&& "Param".equals(((Annotation)modifier).getTypeName().getFullyQualifiedName()))
				{
					return true;
				}
			}
			return false;
		}

		private QuickAssistCompletionProposal init(CompilationUnit astNode, MapperMethod method)
		{
			this.astNode = astNode;
			this.method = method;
			return this;
		}
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
			String targetType = targetMethod.getDeclaringType().getFullyQualifiedName().replace('$',
				'.');
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
					Type returnType = node.getReturnType2();
					if (returnType != null && !isVoid(returnType))
					{
						mapperMethod.setReturnType(getQualifiedNameFromType(returnType));
					}
					return true;
				}
			}
			return false;
		}

		private boolean isVoid(Type type)
		{
			return type.isPrimitiveType()
				&& PrimitiveType.VOID.equals(((PrimitiveType)type).getPrimitiveTypeCode());
		}

		private String getQualifiedNameFromType(Type type)
		{
			ITypeBinding binding = type.resolveBinding();
			if (binding != null)
			{
				return binding.getQualifiedName();
			}
			return null;
		}

		@Override
		public boolean visit(SingleMemberAnnotation node)
		{
			if (nestLevel != 1)
				return false;
			String typeFqn = node.resolveTypeBinding().getQualifiedName();
			Expression value = node.getValue();
			int valueType = value.getNodeType();
			if (MybatipseConstants.ANNOTATION_SELECT.equals(typeFqn)
				|| MybatipseConstants.ANNOTATION_UPDATE.equals(typeFqn)
				|| MybatipseConstants.ANNOTATION_INSERT.equals(typeFqn)
				|| MybatipseConstants.ANNOTATION_DELETE.equals(typeFqn))
			{
				mapperMethod.setSelect(MybatipseConstants.ANNOTATION_SELECT.equals(typeFqn));
				if (valueType == ASTNode.STRING_LITERAL)
				{
					mapperMethod.setStatement(((StringLiteral)value).getLiteralValue());
				}
				else if (valueType == ASTNode.ARRAY_INITIALIZER)
				{
					StringBuilder buffer = new StringBuilder();
					@SuppressWarnings("unchecked")
					List<Expression> expressions = (List<Expression>)((ArrayInitializer)value)
						.expressions();
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
			else if (MybatipseConstants.ANNOTATION_RESULT_MAP.equals(typeFqn))
			{
				if (value.getNodeType() == ASTNode.STRING_LITERAL)
				{
					mapperMethod.setResultMap(((StringLiteral)value).getLiteralValue());
				}
				else if (valueType == ASTNode.ARRAY_INITIALIZER)
				{
					StringBuilder buffer = new StringBuilder();
					@SuppressWarnings("unchecked")
					List<Expression> expressions = (List<Expression>)((ArrayInitializer)value)
						.expressions();
					for (Expression expression : expressions)
					{
						int expressionType = expression.getNodeType();
						if (expressionType == ASTNode.STRING_LITERAL)
						{
							if (buffer.length() > 0)
								buffer.append(',');
							buffer.append(((StringLiteral)expression).getLiteralValue());
						}
						else if (expressionType == ASTNode.INFIX_EXPRESSION)
						{
							buffer.append(parseInfixExpression((InfixExpression)expression));
						}
						mapperMethod.setResultMap(buffer.toString());
					}
				}
			}
			else if (MybatipseConstants.ANNOTATION_MAP_KEY.equals(typeFqn))
			{
				mapperMethod.setHasMapKey(true);
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

		private boolean select;

		private String resultMap;

		private String returnType;

		private boolean hasMapKey;

		public MethodDeclaration getMethodDeclaration()
		{
			return methodDeclaration;
		}

		public void setMethodDeclaration(MethodDeclaration methodDeclaration)
		{
			this.methodDeclaration = methodDeclaration;
		}

		public boolean isSelect()
		{
			return select;
		}

		public void setSelect(boolean select)
		{
			this.select = select;
		}

		public String getStatement()
		{
			return statement;
		}

		public void setStatement(String statement)
		{
			this.statement = statement;
		}

		public String getResultMap()
		{
			return resultMap;
		}

		public void setResultMap(String resultMap)
		{
			this.resultMap = resultMap;
		}

		public String getReturnType()
		{
			return returnType;
		}

		public void setReturnType(String returnType)
		{
			this.returnType = returnType;
		}

		public boolean isHasMapKey()
		{
			return hasMapKey;
		}

		public void setHasMapKey(boolean hasMapKey)
		{
			this.hasMapKey = hasMapKey;
		}

		@SuppressWarnings("rawtypes")
		public List parameters()
		{
			return this.methodDeclaration.parameters();
		}
	}
}
