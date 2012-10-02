/*
 * Copyright 2011-2012 the original author or authors.
 * 
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * 
 *      http://www.apache.org/licenses/LICENSE-2.0
 * 
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.springframework.data.hadoop.hive;

import java.util.List;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.springframework.beans.BeansException;
import org.springframework.beans.factory.BeanFactory;
import org.springframework.beans.factory.BeanFactoryAware;
import org.springframework.beans.factory.FactoryBean;
import org.springframework.util.CollectionUtils;

/**
 * Basic runner for Hive scripts inside a Spring environment. For more advanced functionality, consider using Spring Batch and the {@link HiveTasklet}.
 * 
 * <p/>Note by default, the runner is configured to execute at startup. One can customize this behaviour through {@link #setRunAtStartup(boolean)}.
 * 
 * <p/>This class is a factory bean - if {@link #setRunAtStartup(boolean)} is set to false, then the action (namely the execution of the Hive scripts) is postponed until 
 * {@link #getObject()} is called.
 * 
 * @author Costin Leau
 */
public class HiveRunner extends HiveExecutor implements FactoryBean<List<String>>, BeanFactoryAware {

	private static final Log log = LogFactory.getLog(HiveRunner.class);

	private boolean runAtStartup = false;
	private volatile List<String> result = null;

	private List<String> preActions;
	private List<String> postActions;
	private BeanFactory beanFactory;


	@Override
	public void afterPropertiesSet() {
		super.afterPropertiesSet();

		if (runAtStartup) {
			getObject();
		}
	}

	@Override
	public List<String> getObject() {
		if (result == null) {
			invoke(preActions);
			result = executeHiveScripts();
			invoke(postActions);
		}
		return result;
	}

	@Override
	public Class<?> getObjectType() {
		return List.class;
	}

	@Override
	public boolean isSingleton() {
		return true;
	}

	/**
	 * Indicates whether the scripts should run at container startup or not (the default). 
	 *
	 * @param runAtStartup The runAtStartup to set.
	 */
	public void setRunAtStartup(boolean runAtStartup) {
		this.runAtStartup = runAtStartup;
	}

	/**
	 * Beans to be invoked before running the action.
	 * 
	 * @param beans
	 */
	public void setPreAction(String... beans) {
		this.preActions = CollectionUtils.arrayToList(beans);
	}

	/**
	 * Beans to be invoked after running the action.
	 * 
	 * @param beans
	 */
	public void setPostAction(String... beans) {
		this.postActions = CollectionUtils.arrayToList(beans);
	}

	private void invoke(List<String> beans) {
		if (beanFactory != null) {
			if (!CollectionUtils.isEmpty(beans)) {
				for (String bean : beans) {
					beanFactory.getBean(bean);
				}
			}
		}
		else {
			log.warn("No beanFactory set - cannot invoke pre/post actions [" + beans + "]");
		}
	}

	@Override
	public void setBeanFactory(BeanFactory beanFactory) throws BeansException {
		this.beanFactory = beanFactory;
	}
}