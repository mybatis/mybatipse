
package net.harawata.mybatipse.reader;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import net.harawata.mybatipse.util.MyBatisEditorUiLogger;
import net.harawata.mybatipse.util.MyBatisJavaUtil;
import net.harawata.mybatipse.util.XmlUtil;

import org.eclipse.core.resources.IContainer;
import org.eclipse.core.resources.IFile;
import org.eclipse.core.resources.IResource;
import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.Path;
import org.eclipse.core.runtime.content.IContentDescription;
import org.eclipse.core.runtime.content.IContentType;
import org.eclipse.jdt.core.IJavaElement;
import org.eclipse.jdt.core.IType;
import org.eclipse.jface.text.IDocument;
import org.eclipse.wst.sse.core.StructuredModelManager;
import org.eclipse.wst.sse.core.internal.provisional.IStructuredModel;
import org.eclipse.wst.sse.core.internal.provisional.IndexedRegion;
import org.eclipse.wst.xml.core.internal.provisional.document.IDOMAttr;
import org.eclipse.wst.xml.core.internal.provisional.document.IDOMDocument;
import org.eclipse.wst.xml.core.internal.provisional.document.IDOMModel;
import org.eclipse.wst.xml.core.internal.provisional.document.IDOMNode;
import org.w3c.dom.NamedNodeMap;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

/**
 * @author Peter Hendriks
 */
@SuppressWarnings("restriction")
public class MyBatisDomReader
{

	private static final String IBATIS2_CONTENTTYPE_ID = "net.harawata.mybatipse.ui.ibatis2.sqlmapper";

	private static final String MYBATIS3_CONTENTTYPE_ID = "net.harawata.mybatipse.ui.mybatis3.sqlmapper";

	public IDOMAttr findRelatedAttributeNode(IDOMAttr node)
	{
		String elementName = getElementName(node.getName());
		if (elementName == null)
		{
			return null;
		}
		return (IDOMAttr)findDeclaringNode(node.getModel().getDocument(), elementName,
			node.getValue(), true);
	}

	public List<String> findDeclarations(IDOMDocument document, String sourceElementName)
	{
		List<String> result = new ArrayList<String>();
		NodeList nodeList = document.getElementsByTagName(sourceElementName);
		for (int i = 0; i < nodeList.getLength(); i++)
		{
			Node item = nodeList.item(i);
			String idValue = XmlUtil.getAttributeValue(item, "id");
			if ((idValue != null) && !idValue.trim().isEmpty())
			{
				result.add(idValue);
			}
		}
		return result;
	}

	public IDOMNode findSqlStatement(IFile file, final String statementName)
	{
		IStructuredModel sModel;
		try
		{
			sModel = StructuredModelManager.getModelManager().getModelForRead(file);
		}
		catch (IOException e)
		{
			MyBatisEditorUiLogger.error("Error while opening file " + file, e);
			return null;
		}
		catch (CoreException e)
		{
			MyBatisEditorUiLogger.error("Error while opening file " + file, e);
			return null;
		}
		return new MyBatisDomModelTemplate<IDOMNode>(sModel)
		{
			@Override
			protected IDOMNode doWork(IDOMModel domModel)
			{
				return (IDOMNode)domModel.getDocument().getElementById(statementName);
			}
		}.run();
	}

	public IDOMNode getCurrentMyBatisNode(IDocument document, final int offset)
	{
		return new MyBatisDomModelTemplate<IDOMNode>(StructuredModelManager.getModelManager()
			.getExistingModelForRead(document))
		{
			@Override
			protected IDOMNode doWork(IDOMModel domModel)
			{
				IndexedRegion inode = domModel.getIndexedRegion(offset);
				if (inode == null)
				{
					inode = domModel.getIndexedRegion(offset - 1);
				}
				if (inode instanceof IDOMNode)
				{
					IDOMNode node = (IDOMNode)inode;
					if (node.hasAttributes())
					{
						IDOMNode attributeNode = getAttributeNode(offset, node);
						if (attributeNode != null)
						{
							node = attributeNode;
						}
					}
					return node;
				}
				return null;
			}
		}.run();
	}

	public IFile getRelatedMyBatisMapperFile(IJavaElement element)
	{
		if ((element == null) || (element.getElementType() != IJavaElement.METHOD))
		{
			return null;
		}
		IJavaElement classDef = element.getParent();
		if (classDef.getElementType() != IJavaElement.TYPE)
		{
			return null;
		}
		IType type = (IType)classDef;
		String packageName = type.getPackageFragment().getElementName();
		String mapperName = type.getElementName();
		IContainer[] sourceFolders;
		try
		{
			sourceFolders = MyBatisJavaUtil.getSourceFolders(type.getJavaProject().getProject());
		}
		catch (CoreException e)
		{
			MyBatisEditorUiLogger.error("Error while looking for " + packageName + "." + mapperName,
				e);
			return null;
		}
		Path searchPath = new Path(packageName.replace('.', '/') + "/" + mapperName + ".xml");
		for (IContainer sourceFolder : sourceFolders)
		{
			IFile file = sourceFolder.getFile(searchPath);
			if (file.exists())
			{
				return file;
			}
		}
		return null;
	}

	public IFile getResource(IDOMNode node)
	{
		return (IFile)MyBatisJavaUtil.getResource(new Path(node.getModel().getBaseLocation()));
	}

	private Node findRemoteSource(IFile file, final String sourceElementName,
		final String sourceId, final boolean returnAttribute) throws IOException, CoreException
	{
		return new MyBatisDomModelTemplate<Node>(StructuredModelManager.getModelManager()
			.getModelForRead(file))
		{
			@Override
			protected Node doWork(IDOMModel domModel)
			{
				return findSource(domModel.getDocument(), sourceElementName, sourceId, false,
					returnAttribute);
			}
		}.run();
	}

	private Node findSource(IDOMDocument document, String sourceElementName, String id,
		boolean localSearch, boolean returnAttribute)
	{
		boolean neverSearchNamespace;
		boolean searchOnlyWithNamespace;
		String namespace;
		if (MyBatisDomModelTemplate.MYBATIS_DOCTYPE.equals(document.getDocumentTypeId()))
		{
			neverSearchNamespace = false;
			searchOnlyWithNamespace = false;
		}
		else
		{
			neverSearchNamespace = sourceElementName.equals("sql");
			if (localSearch)
			{
				searchOnlyWithNamespace = false;
			}
			else
			{
				searchOnlyWithNamespace = !neverSearchNamespace;
			}
		}

		if (!neverSearchNamespace)
		{
			namespace = document.getDocumentElement().getAttribute("namespace");
			if ((namespace == null) || (namespace.trim().length() == 0))
			{
				searchOnlyWithNamespace = false;
				neverSearchNamespace = true;
			}
		}
		else
		{
			namespace = null;
		}

		NodeList nodeList = document.getElementsByTagName(sourceElementName);
		for (int i = 0; i < nodeList.getLength(); i++)
		{
			Node item = nodeList.item(i);
			NamedNodeMap attributes = item.getAttributes();
			if (attributes != null)
			{
				Node idNode = attributes.getNamedItem("id");
				if (idNode != null)
				{
					String idValue = idNode.getNodeValue();
					if (isMatch(id, idValue, namespace, neverSearchNamespace, searchOnlyWithNamespace))
					{
						if (returnAttribute)
						{
							return idNode;
						}
						return item;
					}
				}
			}
		}
		// TODO find source from other mapper.xml files
		return null;
	}

	private IDOMNode getAttributeNode(int offset, IDOMNode parent)
	{
		IDOMAttr result = null;
		NamedNodeMap map = parent.getAttributes();

		for (int index = 0; index < map.getLength(); index++)
		{
			IDOMAttr attrNode = (IDOMAttr)map.item(index);
			boolean located = attrNode.contains(offset);
			if (located)
			{
				if (attrNode.hasNameOnly())
				{
					result = null;
				}
				else
				{
					result = attrNode;
				}
				break;
			}
		}
		return result;
	}

	private String getElementName(String nodeName)
	{
		String elementName;
		if (nodeName.equals("refid"))
		{
			elementName = "sql";
		}
		else if (nodeName.equals("resultMap") || nodeName.equals("extends"))
		{
			elementName = "resultMap";
		}
		else if (nodeName.equals("parameterMap"))
		{
			elementName = "parameterMap";
		}
		else
		{
			return null;
		}
		return elementName;
	}

	private boolean isMatch(String id, String idValue, String namespace,
		boolean neverSearchNamespace, boolean searchOnlyWithNamespace)
	{
		// only search with namespace (non-local search on namespace
		// sensitive element
		if (searchOnlyWithNamespace)
		{
			idValue = namespace + "." + idValue;
			if (id.equals(idValue))
			{
				return true;
			}
		}
		else if (!neverSearchNamespace)
		{
			// local search on both namespace and non namespace
			if (id.equals(idValue))
			{
				return true;
			}
			idValue = namespace + "." + idValue;
			if (id.equals(idValue))
			{
				return true;
			}
		}
		else if (id.equals(idValue))
		{
			// only non namespace
			return true;
		}
		return false;
	}

	private Node searchContainer(IContainer container, IResource skipResource,
		String sourceElementName, String sourceId, boolean returnAttribute) throws CoreException,
		IOException
	{
		Node result = null;
		IResource[] members = container.members(IContainer.EXCLUDE_DERIVED);
		for (IResource resource : members)
		{
			if (resource.equals(skipResource))
			{
				continue;
			}
			if (resource instanceof IContainer)
			{
				result = searchContainer((IContainer)resource, skipResource, sourceElementName,
					sourceId, returnAttribute);
			}
			else if (resource instanceof IFile)
			{
				IFile file = (IFile)resource;
				result = searchFile(file, sourceElementName, sourceId, returnAttribute);
			}
			if (result != null)
			{
				break;
			}
		}
		return result;
	}

	private Node searchFile(IFile file, String sourceElementName, String sourceId,
		boolean returnAttribute) throws CoreException, IOException
	{
		if (file.getFileExtension().equals("xml"))
		{
			IContentDescription contentDescription = file.getContentDescription();
			if (contentDescription != null)
			{
				IContentType type = contentDescription.getContentType();
				if ((type != null)
					&& (type.getId().equals(IBATIS2_CONTENTTYPE_ID) || type.getId().equals(
						MYBATIS3_CONTENTTYPE_ID)))
				{
					return findRemoteSource(file, sourceElementName, sourceId, returnAttribute);
				}
			}
		}
		return null;
	}

	public IDOMNode findDeclaringNode(IDOMDocument startingDocument, String sourceElementName,
		String sourceId, boolean returnAttribute)
	{
		Node sourceNode = findSource(startingDocument, sourceElementName, sourceId, true,
			returnAttribute);
		try
		{
			if (sourceNode == null)
			{
				IResource resource = getResource(startingDocument);
				IContainer[] sourceFolders = MyBatisJavaUtil.getSourceFolders(resource.getProject());
				for (IContainer folder : sourceFolders)
				{
					sourceNode = searchContainer(folder, resource, sourceElementName, sourceId,
						returnAttribute);
					if (sourceNode != null)
					{
						break;
					}
				}
			}
			return (IDOMNode)sourceNode;
		}
		catch (CoreException e)
		{
			MyBatisEditorUiLogger.error("Error while looking for " + sourceId, e);
			return null;
		}
		catch (IOException e)
		{
			MyBatisEditorUiLogger.error("Error while looking for " + sourceId, e);
			return null;
		}
	}
}
