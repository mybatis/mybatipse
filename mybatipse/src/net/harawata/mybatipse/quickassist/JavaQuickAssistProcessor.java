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

package net.harawata.mybatipse.quickassist;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;

import org.eclipse.core.resources.IFile;
import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.NullProgressMonitor;
import org.eclipse.core.runtime.Status;
import org.eclipse.jdt.core.ICompilationUnit;
import org.eclipse.jdt.core.IJavaElement;
import org.eclipse.jdt.core.IJavaProject;
import org.eclipse.jdt.core.IMethod;
import org.eclipse.jdt.core.IType;
import org.eclipse.jdt.core.dom.AST;
import org.eclipse.jdt.core.dom.ASTNode;
import org.eclipse.jdt.core.dom.ASTParser;
import org.eclipse.jdt.core.dom.ASTVisitor;
import org.eclipse.jdt.core.dom.Annotation;
import org.eclipse.jdt.core.dom.AnonymousClassDeclaration;
import org.eclipse.jdt.core.dom.ArrayInitializer;
import org.eclipse.jdt.core.dom.ArrayType;
import org.eclipse.jdt.core.dom.BooleanLiteral;
import org.eclipse.jdt.core.dom.CompilationUnit;
import org.eclipse.jdt.core.dom.Expression;
import org.eclipse.jdt.core.dom.ITypeBinding;
import org.eclipse.jdt.core.dom.InfixExpression;
import org.eclipse.jdt.core.dom.MemberValuePair;
import org.eclipse.jdt.core.dom.MethodDeclaration;
import org.eclipse.jdt.core.dom.Name;
import org.eclipse.jdt.core.dom.NormalAnnotation;
import org.eclipse.jdt.core.dom.ParameterizedType;
import org.eclipse.jdt.core.dom.PrimitiveType;
import org.eclipse.jdt.core.dom.QualifiedName;
import org.eclipse.jdt.core.dom.SimpleName;
import org.eclipse.jdt.core.dom.SingleMemberAnnotation;
import org.eclipse.jdt.core.dom.StringLiteral;
import org.eclipse.jdt.core.dom.Type;
import org.eclipse.jdt.core.dom.TypeDeclaration;
import org.eclipse.jdt.core.dom.TypeLiteral;
import org.eclipse.jdt.ui.text.java.IInvocationContext;
import org.eclipse.jdt.ui.text.java.IJavaCompletionProposal;
import org.eclipse.jdt.ui.text.java.IProblemLocation;
import org.eclipse.jdt.ui.text.java.IQuickAssistProcessor;
import org.eclipse.jface.text.IDocument;
import org.eclipse.swt.dnd.Clipboard;
import org.eclipse.swt.dnd.TextTransfer;
import org.eclipse.swt.dnd.Transfer;
import org.eclipse.swt.widgets.Display;

import net.harawata.mybatipse.Activator;
import net.harawata.mybatipse.bean.SupertypeHierarchyCache;
import net.harawata.mybatipse.mybatis.MapperNamespaceCache;
import net.harawata.mybatipse.util.NameUtil;

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
			return null;

		final String mapperFqn = primaryType.getFullyQualifiedName();
		IJavaProject project = compilationUnit.getJavaProject();
		if (project == null)
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
					proposals
						.add(new AddParamQuickAssist("Add @Param to parameters", mapperMethod, astNode));
				}
				if (mapperMethod.getStatementAnno() != null)
				{
					// Copy statement to clipboard
					String annoName = mapperMethod.getStatementAnnoName();
					proposals.add(
						new QuickAssistCompletionProposal("Copy @" + annoName + " statement to clipboard")
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
					// Move statement to XML
					for (IFile xmlMapperFile : MapperNamespaceCache.getInstance()
						.get(project, mapperFqn, null))
					{
						proposals.add(new MoveStatementToXmlQuickAssist(
							"Move @" + annoName + " statement to <" + annoName.toLowerCase() + " /> in "
								+ xmlMapperFile.getFullPath(),
							xmlMapperFile, mapperMethod, astNode));
					}
				}
				if (mapperMethod.getResultsAnno() != null
					|| mapperMethod.getConstructorArgsAnno() != null)
				{
					for (IFile xmlMapperFile : MapperNamespaceCache.getInstance()
						.get(project, mapperFqn, null))
					{
						proposals.add(new MoveResultMapToXmlQuickAssist(
							"Move @Results to <resultMap /> in " + xmlMapperFile.getFullPath(), xmlMapperFile,
							mapperMethod, astNode));
					}
				}
				return proposals.toArray(new IJavaCompletionProposal[proposals.size()]);
			}
		}
		return null;
	}

	private CompilationUnit getAstNode(ICompilationUnit compilationUnit)
	{
		ASTParser parser = ASTParser.newParser(AST.JLS8);
		parser.setSource(compilationUnit);
		parser.setKind(ASTParser.K_COMPILATION_UNIT);
		parser.setResolveBindings(true);
		return (CompilationUnit)parser.createAST(new NullProgressMonitor());
	}

	private MapperMethod getMapperMethod(ASTNode node, IMethod method)
	{
		if (method == null)
			return null;

		QuickAssistMapperMethodVisitor visitor = new QuickAssistMapperMethodVisitor(method);
		node.accept(visitor);
		return visitor.getMapperMethod();
	}

	class QuickAssistMapperMethodVisitor extends ASTVisitor
	{
		private IMethod targetMethod;

		private MapperMethod mapperMethod;

		private int nestLevel;

		private Deque<Annotation> annoStack = new ArrayDeque<Annotation>();

		private ResultAnno resultAnno;

		public QuickAssistMapperMethodVisitor(IMethod targetMethod)
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
		public boolean visit(NormalAnnotation node)
		{
			if (nestLevel != 1)
				return false;
			pushAnno(node);
			return true;
		}

		@Override
		public boolean visit(SingleMemberAnnotation node)
		{
			if (nestLevel != 1)
				return false;
			pushAnno(node);
			parseMemberValuePair(node, "value", node.getValue());
			return true;
		}

		private void pushAnno(Annotation annotation)
		{
			String annoName = getAnnotationName(annotation);
			if ("Result".equals(annoName) || "Arg".equals(annoName))
			{
				resultAnno = new ResultAnno();
			}
			annoStack.push(annotation);
		}

		private String getAnnotationName(Annotation node)
		{
			Name typeName = node.getTypeName();
			if (typeName.isQualifiedName())
				return ((QualifiedName)typeName).getName().getIdentifier();
			else
				return ((SimpleName)typeName).getIdentifier();
		}

		@Override
		public boolean visit(MemberValuePair node)
		{
			String memberName = node.getName().getIdentifier();
			Expression memberValue = node.getValue();
			Annotation currentAnno = annoStack.peek();
			parseMemberValuePair(currentAnno, memberName, memberValue);
			return true;
		}

		private void parseMemberValuePair(Annotation annotation, String memberName,
			Expression memberValue)
		{
			String annoName = getAnnotationName(annotation);
			if ("Select".equals(annoName) || "Insert".equals(annoName) || "Update".equals(annoName)
				|| "Delete".equals(annoName))
			{
				mapperMethod.setStatementAnno(annotation);
				mapperMethod.setStatement(parseStringValue(memberValue, " "));
			}
			else if ("ResultMap".equals(annoName))
			{
				mapperMethod.setResultMap(parseStringValue(memberValue, ","));
			}
			else if ("MapKey".equals(annoName))
			{
				mapperMethod.setHasMapKey(true);
			}
			else if ("ConstructorArgs".equals(annoName))
			{
				mapperMethod.setConstructorArgsAnno(annotation);
			}
			else if ("Results".equals(annoName))
			{
				mapperMethod.setResultsAnno(annotation);
				if ("id".equals(memberName))
				{
					mapperMethod.setResultsId(((StringLiteral)memberValue).getLiteralValue());
				}
			}
			else if ("Result".equals(annoName) || "Arg".equals(annoName))
			{
				if ("id".equals(memberName))
				{
					resultAnno.setId(((BooleanLiteral)memberValue).booleanValue());
				}
				else if ("column".equals(memberName))
				{
					resultAnno.setColumn(((StringLiteral)memberValue).getLiteralValue());
				}
				else if ("property".equals(memberName))
				{
					resultAnno.setProperty(((StringLiteral)memberValue).getLiteralValue());
				}
				else if ("one".equals(memberName))
				{
					resultAnno.setAssociation(true);
				}
				else if ("many".equals(memberName))
				{
					resultAnno.setCollection(true);
				}
				else if ("jdbcType".equals(memberName))
				{
					resultAnno.setJdbcType(((QualifiedName)memberValue).getName().getIdentifier());
				}
				else if ("javaType".equals(memberName))
				{
					// Class<E>
					ITypeBinding binding = ((TypeLiteral)memberValue).resolveTypeBinding();
					resultAnno.setJavaType(binding.getTypeArguments()[0].getQualifiedName());
				}
				else if ("typeHandler".equals(memberName))
				{
					// Class<E>
					ITypeBinding binding = ((TypeLiteral)memberValue).resolveTypeBinding();
					resultAnno.setTypeHandler(binding.getTypeArguments()[0].getQualifiedName());
				}
				else if ("resultMap".equals(memberName))
				{
					resultAnno.setResultMap(((StringLiteral)memberValue).getLiteralValue());
				}
			}
			else if ("One".equals(annoName) || "Many".equals(annoName))
			{
				if ("select".equals(memberName))
				{
					resultAnno.setSelectId(((StringLiteral)memberValue).getLiteralValue());
				}
				else if ("fetchType".equals(memberName))
				{
					String fetchType = ((QualifiedName)memberValue).getName().getIdentifier();
					if (!"DEFAULT".equals(fetchType))
					{
						resultAnno.setFetchType(fetchType.toLowerCase());
					}
				}
			}
		}

		private String parseStringValue(Expression value, String separator)
		{
			int valueType = value.getNodeType();
			if (valueType == ASTNode.STRING_LITERAL)
			{
				return ((StringLiteral)value).getLiteralValue();
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
							buffer.append(separator);
						buffer.append(((StringLiteral)expression).getLiteralValue());
					}
					else if (expressionType == ASTNode.INFIX_EXPRESSION)
					{
						buffer.append(parseInfixExpression((InfixExpression)expression));
					}
				}
				return buffer.toString();
			}
			else if (valueType == ASTNode.INFIX_EXPRESSION)
			{
				return parseInfixExpression((InfixExpression)value);
			}
			Activator.log(Status.ERROR, "Unsupported node type " + valueType);
			return null;
		}

		private String parseInfixExpression(InfixExpression expression)
		{
			// will implement if someone really wants it...
			return expression.toString();
		}

		@Override
		public void endVisit(NormalAnnotation node)
		{
			popAnno();
		}

		@Override
		public void endVisit(SingleMemberAnnotation node)
		{
			popAnno();
		}

		private void popAnno()
		{
			String annoName = getAnnotationName(annoStack.pop());
			if ("Result".equals(annoName))
			{
				mapperMethod.getResultAnnos().add(resultAnno);
				resultAnno = null;
			}
			else if ("Arg".equals(annoName))
			{
				mapperMethod.getConstructorArgs().add(resultAnno);
				resultAnno = null;
			}
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

		private Annotation statementAnno;

		private String statement;

		private String resultMap;

		private boolean hasMapKey;

		private Annotation resultsAnno;

		private String resultsId;

		private Annotation constructorArgsAnno;

		private List<ResultAnno> constructorArgs = new ArrayList<ResultAnno>();

		private List<ResultAnno> resultAnnos = new ArrayList<ResultAnno>();

		public MethodDeclaration getMethodDeclaration()
		{
			return methodDeclaration;
		}

		public void setMethodDeclaration(MethodDeclaration methodDeclaration)
		{
			this.methodDeclaration = methodDeclaration;
		}

		public Annotation getStatementAnno()
		{
			return statementAnno;
		}

		public void setStatementAnno(Annotation statementAnno)
		{
			this.statementAnno = statementAnno;
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

		public boolean isHasMapKey()
		{
			return hasMapKey;
		}

		public void setHasMapKey(boolean hasMapKey)
		{
			this.hasMapKey = hasMapKey;
		}

		public Annotation getResultsAnno()
		{
			return resultsAnno;
		}

		public void setResultsAnno(Annotation resultsAnno)
		{
			this.resultsAnno = resultsAnno;
		}

		public String getResultsId()
		{
			return resultsId;
		}

		public void setResultsId(String resultsId)
		{
			this.resultsId = resultsId;
		}

		public Annotation getConstructorArgsAnno()
		{
			return constructorArgsAnno;
		}

		public void setConstructorArgsAnno(Annotation constructorArgsAnno)
		{
			this.constructorArgsAnno = constructorArgsAnno;
		}

		public List<ResultAnno> getConstructorArgs()
		{
			return constructorArgs;
		}

		public void setConstructorArgs(List<ResultAnno> constructorArgs)
		{
			this.constructorArgs = constructorArgs;
		}

		public List<ResultAnno> getResultAnnos()
		{
			return resultAnnos;
		}

		public void setResultAnnos(List<ResultAnno> resultAnnos)
		{
			this.resultAnnos = resultAnnos;
		}

		@SuppressWarnings("rawtypes")
		public List parameters()
		{
			return this.methodDeclaration.parameters();
		}

		public String getStatementAnnoName()
		{
			Name annoName = this.statementAnno.getTypeName();
			if (annoName.isQualifiedName())
				return ((QualifiedName)annoName).getName().getIdentifier();
			else
				return ((SimpleName)annoName).getIdentifier();
		}

		public boolean isSelect()
		{
			return "Select".equals(getStatementAnnoName());
		}

		public String getReturnTypeStr()
		{
			Type returnType = methodDeclaration.getReturnType2();
			if (returnType == null || isVoid(returnType))
			{
				return null;
			}
			else if (returnType.isPrimitiveType())
			{
				return "_" + returnType.toString();
			}
			else if (returnType.isArrayType())
			{
				Type componentType = ((ArrayType)returnType).getElementType();
				if (componentType.isPrimitiveType())
					return "_" + returnType.toString();
				else
					return NameUtil.stripTypeArguments(componentType.resolveBinding().getQualifiedName());
			}
			else if (returnType.isParameterizedType())
			{
				ParameterizedType parameterizedType = (ParameterizedType)returnType;
				@SuppressWarnings("unchecked")
				List<Type> typeArgs = parameterizedType.typeArguments();
				IType rawType = (IType)parameterizedType.getType().resolveBinding().getJavaElement();
				if (SupertypeHierarchyCache.getInstance().isCollection(rawType))
				{
					if (typeArgs.size() == 1)
						return NameUtil
							.stripTypeArguments(typeArgs.get(0).resolveBinding().getQualifiedName());
				}
				else if (SupertypeHierarchyCache.getInstance().isMap(rawType))
				{
					if (!isHasMapKey())
						return rawType.getFullyQualifiedName();
					else if (typeArgs.size() == 2)
						return NameUtil
							.stripTypeArguments(typeArgs.get(0).resolveBinding().getQualifiedName());
				}
			}
			ITypeBinding binding = returnType.resolveBinding();
			return binding == null ? null : binding.getQualifiedName();
		}

		private boolean isVoid(Type type)
		{
			return type.isPrimitiveType()
				&& PrimitiveType.VOID.equals(((PrimitiveType)type).getPrimitiveTypeCode());
		}
	}

	class ResultAnno
	{
		private boolean id;

		private String column;

		private String property;

		private boolean collection;

		private boolean association;

		private String selectId;

		private String jdbcType;

		private String javaType;

		private String typeHandler;

		private String fetchType;

		private String resultMap;

		public boolean isId()
		{
			return id;
		}

		public void setId(boolean id)
		{
			this.id = id;
		}

		public String getColumn()
		{
			return column;
		}

		public void setColumn(String column)
		{
			this.column = column;
		}

		public String getProperty()
		{
			return property;
		}

		public void setProperty(String property)
		{
			this.property = property;
		}

		public boolean isCollection()
		{
			return collection;
		}

		public void setCollection(boolean collection)
		{
			this.collection = collection;
		}

		public boolean isAssociation()
		{
			return association;
		}

		public void setAssociation(boolean association)
		{
			this.association = association;
		}

		public String getSelectId()
		{
			return selectId;
		}

		public void setSelectId(String selectId)
		{
			this.selectId = selectId;
		}

		public String getJdbcType()
		{
			return jdbcType;
		}

		public void setJdbcType(String jdbcType)
		{
			this.jdbcType = jdbcType;
		}

		public String getJavaType()
		{
			return javaType;
		}

		public void setJavaType(String javaType)
		{
			this.javaType = javaType;
		}

		public String getTypeHandler()
		{
			return typeHandler;
		}

		public void setTypeHandler(String typeHandler)
		{
			this.typeHandler = typeHandler;
		}

		public String getFetchType()
		{
			return fetchType;
		}

		public void setFetchType(String fetchType)
		{
			this.fetchType = fetchType;
		}

		public String getResultMap()
		{
			return resultMap;
		}

		public void setResultMap(String resultMap)
		{
			this.resultMap = resultMap;
		}
	}
}
