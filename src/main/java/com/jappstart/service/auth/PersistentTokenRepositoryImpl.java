package com.jappstart.service.auth;

import java.util.Date;

import org.springframework.security.web.authentication.rememberme.PersistentRememberMeToken;
import org.springframework.security.web.authentication.rememberme.PersistentTokenRepository;
import org.springframework.stereotype.Service;

import com.googlecode.objectify.NotFoundException;
import com.googlecode.objectify.Objectify;
import com.googlecode.objectify.ObjectifyService;
import com.googlecode.objectify.Query;
import com.googlecode.objectify.util.DAOBase;
import com.jappstart.model.auth.PersistentLogin;

@Service
public class PersistentTokenRepositoryImpl extends DAOBase implements PersistentTokenRepository {

	static {
		ObjectifyService.register(PersistentLogin.class);
	}
	
	@Override
	public void createNewToken(PersistentRememberMeToken token) {
		PersistentLogin persistentLogin = new PersistentLogin(token.getUsername(), token.getSeries(), token.getTokenValue(), token.getDate());
		persistWithTransaction(persistentLogin);
	}

	@Override
	public void updateToken(String series, String tokenValue, Date lastUsed) {
		PersistentLogin persistentLogin = null;
		try {
			persistentLogin = ofy().get(PersistentLogin.class, series);
			PersistentLogin updatedPersistentLogin = new PersistentLogin(persistentLogin.getUsername(), persistentLogin.getSeries(), tokenValue, lastUsed);
			persistWithTransaction(updatedPersistentLogin);
		} catch (NotFoundException e) {
			
		}
	}

	@Override
	public PersistentRememberMeToken getTokenForSeries(String seriesId) {
		try {
			PersistentLogin persistentLogin = ofy().get(PersistentLogin.class, seriesId);
			return new PersistentRememberMeToken(persistentLogin.getUsername(), persistentLogin.getSeries(), persistentLogin.getTokenValue(), persistentLogin.getDate());
		} catch (NotFoundException e) {
			return null;
		}
	}

	@Override
	public void removeUserTokens(String username) {
		Query<PersistentLogin> PersistentLogins = ofy().query(PersistentLogin.class).filter("username", username);
		ofy().delete(PersistentLogins);
	}
	
	private static boolean persistWithTransaction(PersistentLogin persistentLogin) {
		boolean completed = true;
		final Objectify transactionalObjectify = ObjectifyService.beginTransaction();
		try {
			transactionalObjectify.put(persistentLogin);
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
