package net.sf.gripes.util

import java.lang.reflect.Field

import javax.servlet.ServletContextEvent
import javax.servlet.ServletContextListener

import net.sf.gripes.model.GripesBaseModel
import net.sf.gripes.util.jetty.*
import net.sf.gripes.util.tomcat.*

import org.mortbay.jetty.servlet.FilterHolder
import org.mortbay.jetty.servlet.FilterMapping
import org.slf4j.Logger
import org.slf4j.LoggerFactory

class GripesContextListener  implements ServletContextListener {
	Logger logger = LoggerFactory.getLogger(GripesContextListener.class)
	
	def context
	
	@Override void contextInitialized(ServletContextEvent contextEvent) {
		logger.info "Loading the Gripes Application..."
		context = contextEvent.getServletContext()
		
		def pack = context.getInitParameter("GripesPackage")+".model"
		
		def tempStr
		try { tempStr = context.TEMPDIR } 
		catch(e) { tempStr = context.getRealPath("/")+"/WEB-INF/work" }
				
		def tempDir = new File(tempStr)
		if(!tempDir.exists()){
			tempDir.mkdirs()
			tempDir.deleteOnExit()
		}
		context.setAttribute("gripes.temp", tempDir.toString())
		
		(new File(this.class.classLoader.getResource(pack.replace(".","/")).getFile())).listFiles().each {
			if(it.isFile()) {
				try {
					def klass
					String klassName = pack.replace("/",".")+"."+it.name.replace(".class","")
					klass = this.class.classLoader.findLoadedClass(klassName) ?:
								this.class.classLoader.findClass(klassName) ?:
									Class.forName(klassName)

					if(klass && klass.getAnnotation(javax.persistence.Entity)){
						GripesBaseModel.crudify(klass)
						
						//if (klass.newInstance().properties.mappings) {					
						//	def builder = Class.forName("net.sf.gripes.entity.builder.GripesEntityBuilder").newInstance(klass)
						//	klass.newInstance().properties.mappings.setDelegate(builder)
						// 	klass.newInstance().properties.mappings.call()
						//}
						
						/*					
						if(klass.newInstance().properties.searchable) {
	 						logger.debug "Setting up GripesSearch"
							def builder = Class.forName("net.sf.gripes.search.builder.GripesSearchBuilder").newInstance(klass)
							klass.newInstance().properties.searchable.setDelegate(builder)
							klass.newInstance().properties.searchable.call()
						}*/
					}
				} catch (e) {
					logger.debug e.message
					logger.debug "Gripes unable to apply CRUD methods to class: ${it}"
				}
			}
		}
		
		def contextHelper = context.getServerInfo().contains("Tomcat")?(new TomcatContextHelper(context)):(new JettyContextHelper(context))
		logger.debug "ContextHelper: $contextHelper"
		
		// TODO need to compensate for the Catalina method of implementing these Filters
		// TODO only use the /gripes-addons/ directory when addon is config'd with "-src"
		def gripesConfig = new ConfigSlurper().parse(this.class.classLoader.getResource("Config.groovy").text)
		context.setAttribute "GripesConfig", gripesConfig
		
		gripesConfig.addons.each {
			def addonName = it

			def addonStartup = GripesHelper.findAddonStartup(addonName)

			if(addonStartup) {
				logger.debug "Running addon startup script"
				def shell = new GroovyShell(this.class.classLoader, new Binding([entityPackage: pack]))
				shell.evaluate(addonStartup)
			}

			def addonConfig = GripesHelper.findAddonConfig(addonName)
								
			def addon = new ConfigSlurper().parse(addonConfig)
			addon.filters.each {k,v ->
				def filterConfig = v
				def holder = contextHelper.getFilter(k)
				logger.debug "Attaching the {} addon to the {}", addonName, k
				if(!holder){
					holder = new FilterHolder(
						heldClass : Class.forName(filterConfig.classname),
						name : k
					)
					def mapping= new FilterMapping(
						filterName : k,
						pathSpec : filterConfig.map,
						dispatches : FilterHolder.dispatch(filterConfig.dispatch)
					)

					contextHelper.addFilter(holder,mapping)	
				}	
				filterConfig.params.each {kk,vv ->
					logger.debug "The {} addon is updating the {} param for the {}", addonName, kk, k
					if(vv.startsWith("+")) 
						holder.setInitParameter(kk,holder.getInitParameter(kk)+","+vv[1..vv.length()-1])
					else
						holder.setInitParameter(kk,vv)
				}
			}
		}
	}
	
	@Override void contextDestroyed(ServletContextEvent contextEvent) {
		context = contextEvent.getServletContext()
		
		logger.info "Gripes Application Shutdown."
	}
}