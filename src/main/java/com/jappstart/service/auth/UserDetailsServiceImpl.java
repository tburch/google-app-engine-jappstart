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
import java.util.List;
import java.util.Locale;

import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.GrantedAuthorityImpl;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;

import com.google.appengine.api.memcache.Expiration;
import com.google.appengine.api.memcache.MemcacheService;
import com.google.appengine.api.taskqueue.Queue;
import com.google.appengine.api.taskqueue.QueueFactory;
import com.google.appengine.api.taskqueue.TaskOptions;
import com.googlecode.objectify.Objectify;
import com.googlecode.objectify.ObjectifyService;
import com.googlecode.objectify.Query;
import com.googlecode.objectify.util.DAOBase;
import com.jappstart.exception.DuplicateUserException;
import com.jappstart.model.auth.UserAccount;

/**
 * The user details service implementation.
 */
@Service
public class UserDetailsServiceImpl extends DAOBase implements EnhancedUserDetailsService {

	static {
		ObjectifyService.register(UserAccount.class);
	}

	/**
	 * The default cache expiration in seconds.
	 */
	private static final int DEFAULT_EXPIRATION = 3600;

	/**
	 * The mail task name.
	 */
	private String mailTaskName;

	/**
	 * The mail task URL.
	 */
	private String mailTaskUrl;

	/**
	 * The memcache service.
	 */
	private MemcacheService memcacheService;

	/**
	 * Sets the mail task name.
	 * 
	 * @param mailTaskName the mail task name
	 */
	public final void setMailTaskName(final String mailTaskName) {
		this.mailTaskName = mailTaskName;
	}

	/**
	 * Sets the mail task URL.
	 * 
	 * @param mailTaskUrl the mail task URL
	 */
	public final void setMailTaskUrl(final String mailTaskUrl) {
		this.mailTaskUrl = mailTaskUrl;
	}

	/**
	 * Sets the memcache service.
	 * 
	 * @param memcacheService the memcache service
	 */
	public final void setMemcacheService(final MemcacheService memcacheService) {
		this.memcacheService = memcacheService;
	}

	/**
	 * Locates the user based on the username.
	 * 
	 * @param username string the username
	 * @return the user details
	 */
	@Override
	public final UserDetails loadUserByUsername(final String username) {
		final List<GrantedAuthority> authorities = new ArrayList<GrantedAuthority>();
		UserAccount user = (UserAccount) memcacheService.get(username);
		if (user == null) {
			final Query<UserAccount> query = ofy().query(UserAccount.class).filter("username", username);
			user = query.get();
			if (user != null) {
				memcacheService.put(username, user, Expiration.byDeltaSeconds(DEFAULT_EXPIRATION));
			} else {
				throw new UsernameNotFoundException("Username not found.");
			}
		}
		authorities.add(new GrantedAuthorityImpl(user.getRole()));
		return new EnhancedUser(user.getUsername(), user.getEmail(), user.getDisplayName(), user.getPassword(), user.getSalt(),
				user.isEnabled(), user.isAccountNonExpired(), user.isCredentialsNonExpired(), user.isAccountNonLocked(), authorities);
	}

	/**
	 * Returns the user account for the given username.
	 * 
	 * @param username the username
	 * @return the user account
	 */
	@Override
	public final UserAccount getUser(final String username) {
		UserAccount user = (UserAccount) memcacheService.get(username);
		if (user == null) {
			final Query<UserAccount> query = ofy().query(UserAccount.class).filter("username", username);
			user = query.get();
			if (user != null) {
				memcacheService.put(username, user, Expiration.byDeltaSeconds(DEFAULT_EXPIRATION));
			}
		}
		return user;
	}

	/**
	 * Adds a user.
	 * 
	 * @param user the user
	 * @param locale the locale
	 */
	@Override
	public final void addUser(final UserAccount user, final Locale locale) {
		final UserAccount cachedUser = (UserAccount) memcacheService.get(user.getUsername());
		if (cachedUser != null) {
			throw new DuplicateUserException();
		}
		final Query<UserAccount> query = ofy().query(UserAccount.class).filter("username", user.getUsername());
		if (query.get() != null) {
			throw new DuplicateUserException();
		}
		final boolean added = persistWithTransaction(user);

		if (added) {
			memcacheService.put(user.getUsername(), user, Expiration.byDeltaSeconds(DEFAULT_EXPIRATION));

			final TaskOptions taskOptions = TaskOptions.Builder.withUrl(mailTaskUrl).param("username", user.getUsername())
					.param("locale", locale.toString());
			final Queue queue = QueueFactory.getQueue(mailTaskName);
			queue.add(taskOptions);
		}
	}

	/**
	 * Activates the user with the given activation key.
	 * 
	 * @param key the activation key
	 * @return true if successful; false otherwise
	 */
	@Override
	public final boolean activateUser(final String key) {
		final Query<UserAccount> query = ofy().query(UserAccount.class).filter("activationKey", key);
		final UserAccount user = query.get();
		boolean activated = true;
		if (user != null) {
			user.setEnabled(true);
			activated = persistWithTransaction(user);
		}
		return activated;
	}

	/**
	 * Indicates if the activation e-mail has been sent.
	 * 
	 * @param username the username
	 * @return true if sent; false otherwise
	 */
	@Override
	public final boolean isActivationEmailSent(final String username) {
		UserAccount user = (UserAccount) memcacheService.get(username);
		if (user == null) {
			final Query<UserAccount> query = ofy().query(UserAccount.class).filter("username", username);
			user = query.get();
			if (user != null) {
				memcacheService.put(username, user, Expiration.byDeltaSeconds(DEFAULT_EXPIRATION));
			} else {
				throw new UsernameNotFoundException("Username not found.");
			}
		}
		return user.isActivationEmailSent();
	}

	/**
	 * Updates the activation e-mail sent status.
	 * 
	 * @param username the username
	 */
	@Override
	public final void activationEmailSent(final String username) {
		final Query<UserAccount> query = ofy().query(UserAccount.class).filter("username", username);
		final UserAccount user = query.get();
		if (user != null) {
			user.setActivationEmailSent(true);
			final boolean udated = persistWithTransaction(user);
			if (udated) {
				memcacheService.put(user.getUsername(), user, Expiration.byDeltaSeconds(DEFAULT_EXPIRATION)); 
			}
		} else {
			throw new UsernameNotFoundException("Username not found.");
		}
	}
	
	/**
	 * Persists a UserAccount using a datastore transaction
	 * 
	 * @param user the {@link UserAccount} to persist
	 * @return true if the datastore transaction was successfully committed to the datastore
	 */
	private static boolean persistWithTransaction(UserAccount user) {
		boolean completed = true;
		final Objectify transactionalObjectify = ObjectifyService.beginTransaction();
		try {
			transactionalObjectify.put(user);
			transactionalObjectify.getTxn().commit();
		} finally {
			if (transactionalObjectify.getTxn().isActive()) {
				transactionalObjectify.getTxn().rollback();
				completed = false;
			}
		}
		return completed;
	}

}
