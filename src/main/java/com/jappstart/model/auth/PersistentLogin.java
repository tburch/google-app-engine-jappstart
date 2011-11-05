package com.jappstart.model.auth;

import java.io.Serializable;
import java.util.Date;

import javax.persistence.Id;

import org.springframework.stereotype.Repository;

import com.googlecode.objectify.annotation.Cached;
import com.googlecode.objectify.annotation.Entity;
import com.googlecode.objectify.annotation.Unindexed;

@Repository
@Entity
@Cached
public class PersistentLogin implements Serializable {
	private static final long serialVersionUID = 1L;
	
	private String username;
	@Id
	private String series;
	@Unindexed
	private String tokenValue;
	@Unindexed
	private Date date;

	@SuppressWarnings("unused")
	private PersistentLogin() {

	}

	public PersistentLogin(String username, String series, String tokenValue, Date date) {
		this.username = username;
		this.series = series;
		this.tokenValue = tokenValue;
		this.date = date;
	}
	
	public String getUsername() {
		return username;
	}

	public String getSeries() {
		return series;
	}

	public String getTokenValue() {
		return tokenValue;
	}

	public Date getDate() {
		return date;
	}
}
