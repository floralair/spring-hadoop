/*
 * Copyright 2013 the original author or authors.
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
package org.springframework.yarn.boot;

import java.util.Properties;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.util.StringUtils;
import org.springframework.yarn.boot.condition.ConditionalOnMissingYarn;
import org.springframework.yarn.boot.support.BootLocalResourcesSelector;
import org.springframework.yarn.boot.support.BootLocalResourcesSelector.Mode;
import org.springframework.yarn.boot.support.SpringYarnClientProperties;
import org.springframework.yarn.boot.support.SpringYarnEnvProperties;
import org.springframework.yarn.boot.support.SpringYarnProperties;
import org.springframework.yarn.client.YarnClient;
import org.springframework.yarn.config.annotation.EnableYarn;
import org.springframework.yarn.config.annotation.EnableYarn.Enable;
import org.springframework.yarn.config.annotation.SpringYarnConfigurerAdapter;
import org.springframework.yarn.config.annotation.builders.YarnClientConfigurer;
import org.springframework.yarn.config.annotation.builders.YarnConfigConfigurer;
import org.springframework.yarn.config.annotation.builders.YarnEnvironmentConfigurer;
import org.springframework.yarn.config.annotation.builders.YarnResourceLocalizerConfigurer;
import org.springframework.yarn.config.annotation.configurers.LocalResourcesHdfsConfigurer;
import org.springframework.yarn.fs.LocalResourcesSelector;
import org.springframework.yarn.fs.LocalResourcesSelector.Entry;
import org.springframework.yarn.launch.LaunchCommandsFactoryBean;

/**
 * {@link EnableAutoConfiguration Auto-configuration} for Spring Hadoop Yarn
 * {@link YarnClient}.
 *
 * @author Janne Valkealahti
 *
 */
@Configuration
@ConditionalOnMissingYarn
@ConditionalOnClass(EnableYarn.class)
@ConditionalOnMissingBean(YarnClient.class)
public class YarnClientAutoConfiguration {

	@Configuration
	@EnableConfigurationProperties({SpringYarnProperties.class, SpringYarnClientProperties.class, SpringYarnEnvProperties.class})
	public static class LocalResourcesSelectorConfig {

		@Autowired
		private SpringYarnClientProperties sycp;

		@Bean
		@ConditionalOnMissingBean(LocalResourcesSelector.class)
		public LocalResourcesSelector localResourcesSelector() {
			BootLocalResourcesSelector selector = new BootLocalResourcesSelector(Mode.APPMASTER);
			if (StringUtils.hasText(sycp.getLocalizerZipPattern())) {
				selector.setZipArchivePattern(sycp.getLocalizerZipPattern());
			}
			if (sycp.getLocalizerPropertiesNames() != null) {
				selector.setPropertiesNames(sycp.getLocalizerPropertiesNames());
			}
			if (sycp.getLocalizerPropertiesSuffixes() != null) {
				selector.setPropertiesSuffixes(sycp.getLocalizerPropertiesSuffixes());
			}
			selector.addPatterns(sycp.getLocalizerPatterns());
			return selector;
		}
	}

	@Configuration
	@EnableConfigurationProperties({SpringYarnProperties.class, SpringYarnClientProperties.class, SpringYarnEnvProperties.class})
	@EnableYarn(enable=Enable.CLIENT)
	public static class SpringYarnConfig extends SpringYarnConfigurerAdapter {

		@Autowired
		private SpringYarnProperties syp;

		@Autowired
		private SpringYarnClientProperties sycp;

		@Autowired
		private LocalResourcesSelector localResourcesSelector;

		@Override
		public void configure(YarnConfigConfigurer config) throws Exception {
			config
				.fileSystemUri(syp.getFsUri())
				.schedulerAddress(syp.getSchedulerAddress())
				.resourceManagerAddress(syp.getRmAddress());
		}

		@Override
		public void configure(YarnResourceLocalizerConfigurer localizer) throws Exception {
			localizer
				.stagingDirectory(syp.getStagingDirectory())
				.withCopy()
					.copy(StringUtils.toStringArray(sycp.getFiles()), syp.getApplicationDir(), syp.getApplicationDir() == null)
					.raw(sycp.getRawFileContents(), syp.getApplicationDir());

			LocalResourcesHdfsConfigurer withHdfs = localizer.withHdfs();
			for (Entry e : localResourcesSelector.select(syp.getApplicationDir() != null ? syp.getApplicationDir() : "/")) {
				withHdfs.hdfs(e.getPath(), e.getType(), syp.getApplicationDir() == null);
			}
		}

		@Override
		public void configure(YarnEnvironmentConfigurer environment) throws Exception {
			environment
				.includeSystemEnv(sycp.isIncludeSystemEnv())
				.withClasspath()
					.includeBaseDirectory(sycp.isIncludeBaseDirectory())
					.defaultYarnAppClasspath(sycp.isDefaultYarnAppClasspath())
					.delimiter(sycp.getDelimiter())
					.entries(sycp.getClasspath())
					.entry(explodedEntryIfZip(sycp));
		}

		@Override
		public void configure(YarnClientConfigurer client) throws Exception {
			client
				.appName(syp.getAppName())
				.appType(syp.getAppType())
				.priority(sycp.getPriority())
				.queue(sycp.getQueue())
				.memory(sycp.getMemory())
				.virtualCores(sycp.getVirtualCores())
				.masterCommands(createMasterCommands(sycp));
		}

	}

	private static String explodedEntryIfZip(SpringYarnClientProperties sycp) {
		return StringUtils.endsWithIgnoreCase(sycp.getAppmasterFile(), ".zip") ? "./" + sycp.getAppmasterFile() : null;
	}

	/**
	 * Builds a raw command set used to start application master.
	 */
	private static String[] createMasterCommands(SpringYarnClientProperties sycp) throws Exception {
		LaunchCommandsFactoryBean factory = new LaunchCommandsFactoryBean();
		String appmasterJar = sycp.getAppmasterFile();

		if (StringUtils.hasText(appmasterJar) && appmasterJar.endsWith("jar")) {
			factory.setJarFile(sycp.getAppmasterFile());
		} else if (StringUtils.hasText(sycp.getMasterRunner())) {
			factory.setRunnerClass(sycp.getMasterRunner());
		} else if (StringUtils.hasText(appmasterJar) && appmasterJar.endsWith("zip")) {
			factory.setRunnerClass("org.springframework.boot.loader.PropertiesLauncher");
		}

		if (sycp.getArguments() != null) {
			Properties arguments = new Properties();
			arguments.putAll(sycp.getArguments());
			factory.setArguments(arguments);
		}

		factory.setOptions(sycp.getOptions());

		factory.setStdout("<LOG_DIR>/Appmaster.stdout");
		factory.setStderr("<LOG_DIR>/Appmaster.stderr");
		factory.afterPropertiesSet();
		return factory.getObject();
	}

}
