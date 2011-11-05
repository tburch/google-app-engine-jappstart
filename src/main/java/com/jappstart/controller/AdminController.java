/*
 *  Copyright (C) 2010 Taylor Leese (tleese22@gmail.com)
 *
 *  This file is part of jappstart.
 *
 *  jappstart is free software: you can redistribute it and/or modify
 *  it under the terms of the GNU Lesser General Public License as published by
 *  the Free Software Foundation, either version 3 of the License, or
 *  (at your option) any later version.
 *
 *  jappstart is distributed in the hope that it will be useful,
 *  but WITHOUT ANY WARRANTY; without even the implied warranty of
 *  MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 *  GNU Lesser General Public License for more details.
 *
 *  You should have received a copy of the GNU Lesser General Public License
 *  along with jappstart.  If not, see <http://www.gnu.org/licenses/>.
 */
package com.jappstart.controller;

import org.springframework.beans.factory.InitializingBean;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;

import com.google.appengine.api.memcache.MemcacheService;
import com.google.appengine.api.memcache.MemcacheServiceFactory;

/**
 * The admin controller.
 */
@Controller
public class AdminController implements InitializingBean {

    /**
     * The memcache service.
     */
    private MemcacheService memcacheService;

    /**
     * Admin.
     *
     * @return the view name
     */
    @RequestMapping(value = "/admin", method = RequestMethod.GET)
    public final String create() {
        return "admin";
    }

    /**
     * Flushes memcache.
     *
     * @return the view name
     */
    @RequestMapping(value = "/admin/flush", method = RequestMethod.GET)
    public final String flushCache() {
        memcacheService.clearAll();
        return "admin";
    }

	@Override
	public void afterPropertiesSet() throws Exception {
		this.memcacheService = MemcacheServiceFactory.getMemcacheService();
	}

}