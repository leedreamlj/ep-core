package com.zhrenjie04.alex.util;

import java.security.Key;
import java.text.SimpleDateFormat;
import java.util.Date;

import javax.annotation.PostConstruct;
import javax.crypto.spec.SecretKeySpec;
import javax.xml.bind.DatatypeConverter;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import io.jsonwebtoken.JwtBuilder;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.SignatureAlgorithm;

/**
 * @author 张人杰
 */
public class JwtUtil {

	private static String jwtAuthKey = "ut2345";

	public static void init(String jwtAutheKey) {
		JwtUtil.jwtAuthKey = Md5Util.encrypt(jwtAutheKey) + JwtUtil.jwtAuthKey;
	}

	public static String encode(String msg) {
		SignatureAlgorithm signatureAlgorithm = SignatureAlgorithm.HS256;
		byte[] apiKeySecretBytes = DatatypeConverter.printBase64Binary(jwtAuthKey.getBytes()).getBytes();
		Key signingKey = new SecretKeySpec(apiKeySecretBytes, signatureAlgorithm.getJcaName());
		JwtBuilder builder = Jwts.builder().setSubject(msg).signWith(signatureAlgorithm, signingKey);
		// 签名,用HS256加密
		String encodedStr = builder.compact();
		return encodedStr;
	}

	public static String decode(String encodedString) {
		if (encodedString == null || "".equals(encodedString)) {
			return null;
		}
		byte[] apiKeySecretBytes = DatatypeConverter.printBase64Binary(jwtAuthKey.getBytes()).getBytes();
		String decodedString = Jwts.parser().setSigningKey(apiKeySecretBytes).parseClaimsJws(encodedString).getBody()
				.getSubject();
		return decodedString;
	}

	public static void main(String[] args) {
		System.out.println("jwtAuthKey:" + jwtAuthKey);
		String token = JwtUtil.encode("{\"userId\":1}");
		System.out.println("encoded:" + token);
		String msg = JwtUtil.decode(token);
		System.out.println("decoded:" + msg);
		SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");
		Date now = new Date();
		token = JwtUtil.encode("{\"userId\":\"1\",\"phoneNo\":\"13910326704\",\"realName\":\"系统管理员\",\"loginTime\":\""
				+ sdf.format(now) + "\"}");
		System.out.println(token);
		msg = JwtUtil.decode(token);
		System.out.println("decoded:" + msg);
	}
}
