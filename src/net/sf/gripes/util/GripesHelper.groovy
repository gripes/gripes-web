package net.sf.gripes.util

import org.slf4j.Logger
import org.slf4j.LoggerFactory

class GripesHelper {
	static Logger _logger = LoggerFactory.getLogger(GripesContextListener.class)	
	
	static def findAddon(File base, String name) {
		
	}
	
	static String findAddonStartup(String addonName) {
		def addonStartup = this.classLoader.getResource("gripes/addons/${addonName}/gripes.startup") ?:
							this.classLoader.getResource("gripes/gripes-addons/${addonName}/gripes.startup")
							
		if(!addonStartup) {
			return new File("../${addonName}/gripes.startup").text
		} else {		
			addonStartup.text	
		}
	}
	
	static def findAddonConfig(String addonName) {
		def addonConfig = this.classLoader.getResource("gripes/addons/${addonName}/gripes.addon") ?:
							this.classLoader.getResource("gripes/gripes-addons/${addonName}/gripes.addon")
		
		if(!addonConfig) {
			addonConfig = new File("../${addonName}/gripes.addon").text
		}
		
		addonConfig
	}
}
