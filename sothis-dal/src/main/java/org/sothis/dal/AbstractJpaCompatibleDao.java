package org.sothis.dal;

import java.beans.PropertyDescriptor;
import java.io.Serializable;
import java.lang.reflect.Method;
import java.util.Collections;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

import javax.persistence.Column;
import javax.persistence.GeneratedValue;
import javax.persistence.Id;
import javax.persistence.Table;
import javax.persistence.Transient;

import org.apache.commons.beanutils.PropertyUtils;
import org.apache.commons.lang.StringUtils;
import org.sothis.dal.query.Chain;
import org.sothis.dal.query.Cnd;
import org.sothis.dal.query.Op;

/**
 * ʵ���˻���JPA���ֶ�ӳ��
 * 
 * @author velna
 * 
 * @param <E>
 * @param <K>
 */
public abstract class AbstractJpaCompatibleDao<E extends Entity<K>, K extends Serializable> extends AbstractDao<E, K> {

	private final String tableName;
	private final Map<String, PropertyInfo> fieldMap;
	private final Map<String, PropertyInfo> propertyMap;
	private String idColumnName;
	private boolean idGeneratedValue;

	public AbstractJpaCompatibleDao() {
		// entity annotation
		javax.persistence.Entity entity = this.getEntityClass().getAnnotation(javax.persistence.Entity.class);
		if (null == entity) {
			throw new RuntimeException("no Entity annotation found of entity class " + this.getEntityClass().getName());
		}

		// table annotation
		Table table = this.getEntityClass().getAnnotation(Table.class);
		if (null == table) {
			throw new RuntimeException("no Table annotation found of entity class " + this.getEntityClass().getName());
		}
		tableName = table.name();
		if (StringUtils.isEmpty(tableName)) {
			throw new RuntimeException("name of Table annotation is empty of entity class " + this.getEntityClass().getName());
		}
		if (!tableName.toLowerCase().equals(tableName)) {
			throw new IllegalArgumentException("table name of class  [" + this.getEntityClass().getName() + "] must be lower cased, current is [" + tableName + "]");
		}

		// fields annotation
		PropertyDescriptor[] propertyDescriptors = PropertyUtils.getPropertyDescriptors(this.getEntityClass());
		Map<String, PropertyInfo> _fieldMap = new HashMap<String, PropertyInfo>(propertyDescriptors.length);
		Map<String, PropertyInfo> _propertyMap = new HashMap<String, PropertyInfo>(propertyDescriptors.length);
		boolean idFind = false;
		for (PropertyDescriptor descriptor : propertyDescriptors) {
			Method readMethod = descriptor.getReadMethod();
			if (null != readMethod) {
				Column column = readMethod.getAnnotation(Column.class);
				if (null == column) {
					continue;
				}
				Id id = readMethod.getAnnotation(Id.class);
				if (null != id) {
					if (idFind) {
						throw new RuntimeException("multi Id annotation found of entity class " + this.getEntityClass().getName());
					}
					idFind = true;
					idColumnName = descriptor.getName();
					GeneratedValue generatedValue = readMethod.getAnnotation(GeneratedValue.class);
					idGeneratedValue = generatedValue != null;
				}
				Transient aTransient = readMethod.getAnnotation(Transient.class);
				PropertyInfo info = new PropertyInfo(descriptor, column, null != id, null != aTransient, this.getEntityClass());
				_propertyMap.put(descriptor.getName(), info);
				_fieldMap.put(column.name(), info);
			}
		}
		propertyMap = Collections.unmodifiableMap(_propertyMap);
		fieldMap = Collections.unmodifiableMap(_fieldMap);

	}

	/**
	 * �õ�����
	 * 
	 * @return
	 */
	public String getTableName() {
		return tableName;
	}

	/**
	 * �õ��ֶ�ӳ�����keyΪʵ������ֶ���������ʵ�����ݿ���ֶ�����
	 * 
	 * @return
	 */
	public Map<String, PropertyInfo> getPropertyMap() {
		return propertyMap;
	}

	/**
	 * �õ��ֶ�property��ӳ�䣬propertyΪʵ������ֶ���������ʵ�����ݿ���ֶ�����
	 * 
	 * @param property
	 * @return
	 */
	public PropertyInfo getPropertyInfoByProperty(String property) {
		PropertyInfo ret = propertyMap.get(property);
		if (null == ret) {
			throw new IllegalArgumentException("no property named [" + property + "] found of entity class " + this.getEntityClass().getName());
		}
		return ret;
	}

	/**
	 * �õ����ݿ��ֶ�ӳ�����keyΪ���ݿ���ֶ���������ʵ������ֶ�����
	 * 
	 * @return
	 */
	public Map<String, PropertyInfo> getFieldMap() {
		return fieldMap;
	}

	/**
	 * �õ����ݿ��ֶ�field��ӳ�䣬fieldΪ���ݿ���ֶ���������ʵ������ֶ�����
	 * 
	 * @param field
	 * @return
	 */
	public PropertyInfo getPropertyInfoByField(String field) {
		PropertyInfo ret = fieldMap.get(field);
		if (null == ret) {
			throw new IllegalArgumentException("no field named [" + field + "] found of table " + tableName);
		}
		return ret;
	}

	/**
	 * �õ�id�ֶε��ֶ���
	 * 
	 * @return
	 */
	public String getIdColumnName() {
		return idColumnName;
	}

	/**
	 * id�ֶ��Ƿ�Ϊ�Զ�����
	 * 
	 * @return
	 */
	public boolean isIdGeneratedValue() {
		return idGeneratedValue;
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see com.fangjia.dal.EntityDao#update(com.fangjia.dal.Entity)
	 */
	@SuppressWarnings("unchecked")
	public E update(E entity) {
		if (null == entity) {
			throw new IllegalArgumentException("entity  is null ");
		}
		K id = null;
		Chain chain = Chain.make();
		Iterator<Map.Entry<String, PropertyInfo>> iterator = propertyMap.entrySet().iterator();
		while (iterator.hasNext()) {
			Map.Entry<String, PropertyInfo> entry = iterator.next();
			PropertyInfo propertyInfo = entry.getValue();
			if (propertyInfo.isID()) {
				PropertyDescriptor descriptor = propertyInfo.getPropertyDescriptor();
				Method readMethod = descriptor.getReadMethod();
				try {
					id = (K) readMethod.invoke(entity, new Object[] {});
				} catch (Exception e) {
					throw new IllegalArgumentException(entity.getClass().getName(), e);
				}
				continue;
			}
			if (propertyInfo.isTransient()) {
				continue;
			}
			PropertyDescriptor descriptor = propertyInfo.getPropertyDescriptor();
			Method readMethod = descriptor.getReadMethod();
			String columnName = descriptor.getName();
			try {
				Object obj = readMethod.invoke(entity, new Object[] {});
				chain.add(columnName, obj);
			} catch (Exception e) {
				throw new IllegalArgumentException(entity.getClass().getName(), e);
			}
		}
		if (null == id) {
			throw new IllegalArgumentException(entity.getClass().getName() + " ID is null !");
		}
		if (chain.size() > 0) {
			updateById(id, chain);
		}
		return entity;
	}

	private void assertIdColumnNameNotNull() {
		if (null == this.getIdColumnName()) {
			throw new IllegalStateException("no id column defined");
		}
	}

	@Override
	public E findById(K id) {
		assertIdColumnNameNotNull();
		return findOne(Cnd.where(this.getIdColumnName(), id), null);
	}

	@Override
	public List<E> findByIds(List<K> idList) {
		assertIdColumnNameNotNull();
		return find(Cnd.where(this.getIdColumnName(), Op.In, idList), null, null);
	}

	@Override
	public int updateById(K id, Chain chain) {
		assertIdColumnNameNotNull();
		return update(Cnd.where(this.getIdColumnName(), id), chain);
	}

	@Override
	public int deleteById(K id) {
		assertIdColumnNameNotNull();
		return delete(Cnd.where(this.getIdColumnName(), id));
	}

	@Override
	public int deleteByIds(List<K> idList) {
		assertIdColumnNameNotNull();
		return delete(Cnd.where(this.getIdColumnName(), Op.In, idList));
	}

	/**
	 * ʵ�����ֶ�������Ϣ
	 * 
	 * @author velna
	 * 
	 */
	public static class PropertyInfo {
		private final PropertyDescriptor propertyDescriptor;
		private final Column column;
		private final Class<?> clazz;
		private final boolean isId;
		private final boolean transients;

		public PropertyInfo(PropertyDescriptor propertyDescriptor, Column column, boolean isId, boolean transients, Class<?> clazz) {
			if (!column.name().toLowerCase().equals(column.name())) {
				throw new IllegalArgumentException("name of column [" + propertyDescriptor.getName() + "] must be lower cased of class " + clazz.getName() + ", current is ["
						+ column.name() + "]");
			}
			this.isId = isId;
			this.propertyDescriptor = propertyDescriptor;
			this.column = column;
			this.clazz = clazz;
			this.transients = transients;
		}

		/**
		 * �õ��ֶε�������Ϣ
		 * 
		 * @return
		 */
		public PropertyDescriptor getPropertyDescriptor() {
			return propertyDescriptor;
		}

		/**
		 * �õ�Columnע��
		 * 
		 * @return
		 */
		public Column getColumn() {
			return column;
		}

		/**
		 * �Ƿ�Ϊid�ֶ�
		 * 
		 * @return
		 */
		public boolean isID() {
			return isId;
		}

		/**
		 * �õ�������ʵ����
		 * 
		 * @return
		 */
		public Class<?> getClazz() {
			return clazz;
		}

		/**
		 * �Ƿ�Ϊ˲̬��
		 * 
		 * @return
		 */
		public boolean isTransient() {
			return transients;
		}

	}
}