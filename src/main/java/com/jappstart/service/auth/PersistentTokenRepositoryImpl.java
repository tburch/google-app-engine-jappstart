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
package com.jappstart.service.auth;

import java.util.ArrayList;
import java.util.Date;

import org.springframework.security.web.authentication.rememberme.PersistentRememberMeToken;
import org.springframework.security.web.authentication.rememberme.PersistentTokenRepository;
import org.springframework.stereotype.Service;

import com.google.appengine.api.memcache.Expiration;
import com.google.appengine.api.memcache.MemcacheService;
import com.googlecode.objectify.Objectify;
import com.googlecode.objectify.ObjectifyService;
import com.googlecode.objectify.Query;
import com.googlecode.objectify.util.DAOBase;
import com.jappstart.model.auth.PersistentLogin;
import com.jappstart.model.auth.PersistentUser;
import com.jappstart.model.auth.UserAccount;

/**
 * The persistent token repository implementation.
 */
@Service
public class PersistentTokenRepositoryImpl extends DAOBase implements PersistentTokenRepository {

	static {
		ObjectifyService.register(PersistentLogin.class);
	}

	/**
	 * The default cache expiration in seconds.
	 */
	private static final int DEFAULT_EXPIRATION = 3600;

	/**
	 * The memcache service.
	 */
	private MemcacheService memcacheService;

	/**
	 * Sets the memcache service.
	 * 
	 * @param memcacheService the memcache service
	 */
	public final void setMemcacheService(final MemcacheService memcacheService) {
		this.memcacheService = memcacheService;
	}

	/**
	 * Creates a new remember me token.
	 * 
	 * @param token the remember me token
	 */
	@Override
	public final void createNewToken(final PersistentRememberMeToken token) {
		final Query<UserAccount> query = ofy().query(UserAccount.class).filter("username", token.getUsername());
		final UserAccount user = query.get();
		if (user != null) {
			if (user.getPersistentUser() == null) {
				user.setPersistentUser(new PersistentUser(user.getId(), token.getUsername()));
			}
			if (user.getPersistentUser().getPersistentLogins() == null) {
				user.getPersistentUser().setPersistentLogins(new ArrayList<PersistentLogin>());
			}
			user.getPersistentUser().getPersistentLogins().add(createPersistentLogin(user.getPersistentUser().getId(), token));
			final Objectify transactionalObjectify = ObjectifyService.beginTransaction();
			boolean completed = true;
			try {
				transactionalObjectify.put(user);
				transactionalObjectify.getTxn().commit();
			} finally {
				if (transactionalObjectify.getTxn().isActive()) {
					transactionalObjectify.getTxn().rollback();
					completed = false;
				}
			}
			if (completed) {
				memcacheService.put(user.getUsername(), user, Expiration.byDeltaSeconds(DEFAULT_EXPIRATION));
			}
		}
	}

	/**
	 * Gets the token for the given series.
	 * 
	 * @param series  the series
	 * @return the remember me token
	 */
	@Override
	public final PersistentRememberMeToken getTokenForSeries(final String series) {
		final Query<PersistentLogin> query = ofy().query(PersistentLogin.class).filter("series", series);
		PersistentLogin persistentLogin = query.get();
		if (persistentLogin == null) {
			return null;
		}
		return new PersistentRememberMeToken(persistentLogin.getUsername(), persistentLogin.getSeries(), persistentLogin.getToken(),
				persistentLogin.getLastUsed());
	}

	/**
	 * Removes the tokens for the given username.
	 * 
	 * @param username the username
	 */
	@Override
	public final void removeUserTokens(final String username) {
		final Query<PersistentLogin> query = ofy().query(PersistentLogin.class).filter("username", username);
		PersistentLogin persistentLogin = query.get();
		if (persistentLogin != null) {
			final Objectify transactionalObjectify = ObjectifyService.beginTransaction();
			try {
				transactionalObjectify.delete(persistentLogin);
				transactionalObjectify.getTxn().commit();
			} finally {
				if (transactionalObjectify.getTxn().isActive()) {
					transactionalObjectify.getTxn().rollback();
				}
			}
		}
	}

	/**
	 * Updates the token given the series, token value, and last used date.
	 * 
	 * @param series the series
	 * @param tokenValue the token value
	 * @param lastUsed the last used date
	 */
	@Override
	public final void updateToken(final String series, final String tokenValue, final Date lastUsed) {
		final Query<PersistentLogin> query = ofy().query(PersistentLogin.class).filter("series", series);
		PersistentLogin persistentLogin = query.get();
		if (persistentLogin != null) {
			persistentLogin.setToken(tokenValue);
			persistentLogin.setLastUsed(lastUsed);
			final Objectify transactionalObjectify = ObjectifyService.beginTransaction();
			try {
				transactionalObjectify.delete(persistentLogin);
				transactionalObjectify.getTxn().commit();
			} finally {
				if (transactionalObjectify.getTxn().isActive()) {
					transactionalObjectify.getTxn().rollback();
				}
			}
		}
	}
	
	/**
	 * Creates a persistent login given a remember me token.
	 * 
	 * @param id the parent key
	 * @param token the remember me token
	 * @return the persistent login
	 */
	private PersistentLogin createPersistentLogin(final long id, final PersistentRememberMeToken token) {
		final PersistentLogin persistentLogin = new PersistentLogin(id, token.getUsername());
		persistentLogin.setSeries(token.getSeries());
		persistentLogin.setToken(token.getTokenValue());
		persistentLogin.setLastUsed(token.getDate());
		return persistentLogin;
	}

}
