
package org.eclipselabs.mybatiseditor.ui.reader;

import org.eclipse.wst.sse.core.internal.provisional.IStructuredModel;
import org.eclipse.wst.xml.core.internal.provisional.document.IDOMModel;

/**
 * Utility class for working with an XML DOM model, validating that it is a MyBatis or iBatis
 * mapper file, and releases the model when done.
 * 
 * @author Peter Hendriks
 * @param <T> The return type of the {@link #doWork(IDOMModel)} method.
 */
@SuppressWarnings("restriction")
abstract class MyBatisDomModelTemplate<T>
{

	public static final String MYBATIS_DOCTYPE = "-//mybatis.org//DTD Mapper 3.0//EN";

	public static final String IBATIS_DOCTYPE = "-//ibatis.apache.org//DTD SQL Map 2.0//EN";

	private final IStructuredModel sModel;

	public MyBatisDomModelTemplate(IStructuredModel sModel)
	{
		this.sModel = sModel;
	}

	/**
	 * Implement this method to actually do something when running the template.
	 * 
	 * @param domModel The validated model.
	 * @return The result, or <code>null</code> if no result.
	 */
	protected abstract T doWork(IDOMModel domModel);

	public T run()
	{
		try
		{
			if (sModel instanceof IDOMModel)
			{
				IDOMModel domModel = (IDOMModel)sModel;
				String documentTypeId = domModel.getDocument().getDocumentTypeId();
				if (!MYBATIS_DOCTYPE.equals(documentTypeId) && !IBATIS_DOCTYPE.equals(documentTypeId))
				{
					return null;
				}
				return doWork(domModel);
			}
			return null;
		}
		finally
		{
			if (sModel != null)
			{
				sModel.releaseFromRead();
			}
		}
	}
}
