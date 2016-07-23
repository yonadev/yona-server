package nu.yona.server.entities;

import org.hibernate.proxy.HibernateProxy;

public class EntityUtil
{
	private EntityUtil()
	{
		// No instances
	}

	@SuppressWarnings("unchecked")
	public static <T> T enforceLoading(T entity)
	{
		if (entity instanceof HibernateProxy)
		{
			// See http://stackoverflow.com/a/2216603/4353482
			// Simply returning the value causes a failure in instanceof and cast
			return (T) ((HibernateProxy) entity).getHibernateLazyInitializer().getImplementation();
		}
		return entity;
	}
}
