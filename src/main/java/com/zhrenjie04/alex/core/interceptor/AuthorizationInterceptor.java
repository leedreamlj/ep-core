package com.zhrenjie04.alex.core.interceptor;

import java.util.ArrayList;
import java.util.Date;
import java.util.UUID;

import javax.annotation.PostConstruct;
import javax.servlet.http.Cookie;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.servlet.HandlerInterceptor;
import org.springframework.web.servlet.ModelAndView;

import com.zhrenjie04.alex.core.Permission;
import com.zhrenjie04.alex.core.User;
import com.zhrenjie04.alex.core.exception.CrisisError;
import com.zhrenjie04.alex.core.exception.UnauthorizedException;
import com.zhrenjie04.alex.util.JsonUtil;
import com.zhrenjie04.alex.util.JwtUtil;
import com.zhrenjie04.alex.util.RedisUtil;
import com.zhrenjie04.alex.util.SessionUtil;

/**
 * @author 张人杰
 */
public class AuthorizationInterceptor implements HandlerInterceptor {

	public static final String AUTHORIZATION_HEADER_KEY = "sid";

	@Value("${auth.open-swagger}")
	private Boolean openSwagger;
	@Value("${auth.system-code}")
	private String systemCode;
	@Value("${auth.unfiltered-codes}")
	private String unfilteredCodesString;
	private ArrayList<String> unfilteredCodes;

	@Value("${auth.default-authorized-codes}")
	private String defaultAuthorizedCodesString;
	private ArrayList<String> defaultAuthorizedCodes;

	@Value("${auth.unfilteredPathStartWith}")
	private String unfilteredPathStartWithString;
	private ArrayList<String> unfilteredPathStartWith;

	Logger logger = LoggerFactory.getLogger(AuthorizationInterceptor.class);

	@PostConstruct
	protected void init() {
		unfilteredCodes = new ArrayList<>();
		String[] codes = unfilteredCodesString.split("\\,");
		if (codes != null) {
			for (String code : codes) {
				unfilteredCodes.add(code);
			}
		}
		defaultAuthorizedCodes = new ArrayList<>();
		codes = defaultAuthorizedCodesString.split("\\,");
		if (codes != null) {
			for (String code : codes) {
				defaultAuthorizedCodes.add(code);
			}
		}
		unfilteredPathStartWith=new ArrayList<>();
		String[] starts=unfilteredPathStartWithString.split("\\,");
		if(starts!=null) {
			for(String start:starts) {
				unfilteredPathStartWith.add(start);
			}
		}
	}

	private String getPrivilegeCode(HandlerMethod method) {
		String code = "";
		Permission permission = method.getBean().getClass().getAnnotation(Permission.class);
		if (permission == null) {
			throw new CrisisError("BACKEND SYSTEM IS NOT ALLOWED TO CALL CLASS WITH NO PERMISSION ANNOTATION");
		}
		code += permission.value();
		permission = method.getMethod().getAnnotation(Permission.class);
		if (permission == null) {
			throw new CrisisError("BACKEND SYSTEM IS NOT ALLOWED TO CALL METHOD WITH NO PERMISSION ANNOTATION");
		}
		code += "." + permission.value();
		return code;
	}
	private Boolean getIsBackMethod(HandlerMethod method) {
		String packageName=method.getBean().getClass().getPackage().getName();
		logger.debug("getIsBackMethod::::::{}",packageName);
		return packageName.endsWith(".back");
	}
	@Override
	public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler)
			throws Exception {
		// 从cookie获取sid
		String sid = null;
		Cookie[] cookies = request.getCookies();
		if (cookies != null) {
			for (Cookie cookie : cookies) {
				if ("sid".equals(cookie.getName())) {
					sid = cookie.getValue();
					break;
				}
			}
		}
		if(sid==null) {
			sid = "t-" + UUID.randomUUID();
			Cookie cookie = new Cookie("sid", sid);
			cookie.setPath("/");
			cookie.setMaxAge(-1);
			response.addCookie(cookie);
		}
		request.setAttribute("sid", sid);
		// 处理jwt头部
		String token=request.getHeader("token");
		// 跳过不需要处理的路径
		for(String starts:unfilteredPathStartWith) {
			if (request.getRequestURI().startsWith(starts)) {
				return true;
			}
		}
		if ("/error".equals(request.getRequestURI())) {
			return true;
		}
		if (openSwagger && (request.getRequestURI().startsWith("/swagger-resources")
				|| request.getRequestURI().startsWith("/v2/api-docs")||request.getRequestURI().startsWith("/swagger-ui.html"))||request.getRequestURI().startsWith("/webjars")) {
			return true;
		}
		User user = SessionUtil.getSessionUser(request);
		request.setAttribute("user", user);
		HandlerMethod method = (HandlerMethod) handler;
		String needCode = systemCode + ":" + getPrivilegeCode(method);
		// 不需要验证权限（不需要登录），则生成sid
		if (unfilteredCodes.contains(needCode)) {
			return true;
		}
		// 需要验证权限
		if (token == null && user == null) {
			throw new UnauthorizedException("您尚未登录");
		}
		if(user == null && token!=null && !"".equals(token) && !getIsBackMethod(method)) {//redis session优先,后端程序不通过token验证权限
			user = JsonUtil.parse(JwtUtil.decode(token),User.class);
		}
		if (user == null) {
			throw new UnauthorizedException("session已失效");
		}
		if(user.getOtherParams().get("endLineTime")==null||(long)(user.getOtherParams().get("endLineTime"))<new Date().getTime()) {
			throw new UnauthorizedException("session已失效");
		}
		if(RedisUtil.sismember("banedTokens", token)) {
			throw new UnauthorizedException("session已失效");
		}
		if(RedisUtil.sismember("banedUserIds", user.getUserId())) {
			throw new UnauthorizedException("您已被系统拉入黑名单，无法做任何操作");
		}
		// 所有访问都需要权限验证
		if (defaultAuthorizedCodes.contains(needCode) || user.getPrivilegeCodes().contains(needCode)) {
			request.setAttribute("user", user);
			return true;
		} else {
			throw new UnauthorizedException("没有对应的权限");
		}
	}

	@Override
	public void postHandle(HttpServletRequest request, HttpServletResponse response, Object handler,
			ModelAndView modelAndView) throws Exception {
	}

	@Override
	public void afterCompletion(HttpServletRequest request, HttpServletResponse response, Object handler, Exception ex)
			throws Exception {
	}

}
