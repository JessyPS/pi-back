package com.equipe7.getserv.controller;

import java.io.IOException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.auth0.jwt.JWT;
import com.auth0.jwt.interfaces.DecodedJWT;
import com.auth0.jwt.interfaces.JWTVerifier;
import com.equipe7.getserv.controller.form.RoleToUserForm;
import com.equipe7.getserv.controller.form.SignInForm;
import com.equipe7.getserv.controller.form.SignUpForm;
import com.equipe7.getserv.model.RegisterEntity;
import com.equipe7.getserv.model.UserEntity;
import com.equipe7.getserv.repository.RoleRepository;
import com.equipe7.getserv.resource.Token;
import com.equipe7.getserv.service.UserService;
import com.fasterxml.jackson.databind.ObjectMapper;

@RestController
@RequestMapping("")
public class UserController {
	
	private final UserService userServ;
	
	@Autowired
	private RoleRepository repository;
	
	public UserController(UserService userServ) {
		super();
		this.userServ = userServ;
	}
	
	@PostMapping("/signup")
	public ResponseEntity<?> signUp(@RequestBody SignUpForm form){
		UserEntity user = new UserEntity();
		user.setUsername(form.getUsername());
		user.setPassword(form.getPassword());

		if (user.errors.size() > 0)
			return ResponseEntity.badRequest().body(user.errors);
		
		user.getRoles().add(repository.findByName("ROLE_USER"));
		
		RegisterEntity register = new RegisterEntity();
		
		register.setName(form.getName());
		register.setCpf(form.getCpf());
		register.setEmail(form.getEmail());
		register.setBirthday(form.getBirthday());

		if (register.errors.size() > 0)
			return ResponseEntity.badRequest().body(register.errors);
		
		user.setRegister(register);
		register.setUser(user);
		
		userServ.encodePassword(user);
		return postUser(user);
	}
	
	@PostMapping("/signin")
	public ResponseEntity<?> signIn(@RequestBody SignInForm form, HttpServletRequest request) {
		UserEntity user = userServ.getUser(form.getUsername());
		
		if (user == null)
			return ResponseEntity.status(HttpStatus.NOT_FOUND).body("Usuário inválido");
		
		if (!userServ.matches(form.getPassword(), user))
			return ResponseEntity.status(HttpStatus.NOT_FOUND).body("Senha inválida");
		
		System.out.println(request.getCookies());
		
		return ResponseEntity.ok(Token.createTokens(user));
	}

	@GetMapping("/users")
	public ResponseEntity<List<UserEntity>> getUsers(){
		return ResponseEntity.ok().body(userServ.getUsers());
	}

	@PostMapping("/post/user")
	public ResponseEntity<UserEntity> postUser(UserEntity user){
		return ResponseEntity.created(null).body(userServ.saveUser(user));
	}

	/*@PostMapping("/post/role")
	public ResponseEntity<RoleEntity> postUser(RoleEntity role){
		return ResponseEntity.created(null).body(userService.createRole(role));
	}*/

	@PostMapping("/post/addrole")
	public ResponseEntity<?> postToUser(RoleToUserForm form){
		userServ.addRoleToUser(form.getUsername(), form.getRoleName());
		return ResponseEntity.ok().build();
	}
	
	@GetMapping("/token/refresh")
	public void refresh(HttpServletRequest request, HttpServletResponse response) throws IOException{
		String authorizationHeader = request.getHeader(HttpHeaders.AUTHORIZATION);
		if (authorizationHeader != null && authorizationHeader.startsWith(Token.START)) {
			try {
				String refresh_token = authorizationHeader.substring(Token.START.length());
				JWTVerifier varifier = JWT.require(Token.ALGORITHM).build();
				DecodedJWT decodedJWT = varifier.verify(refresh_token);
				String username = decodedJWT.getSubject();
				UserEntity user = userServ.getUser(username);
				String access_token = Token.createAccessToken(user, 90l * 60000l);
				
				Map<String, String> tokens = new HashMap<>();
				tokens.put("access_token", access_token);
				tokens.put("refresh_token", Token.START + refresh_token);
				response.setContentType(MediaType.APPLICATION_JSON_VALUE);
				new ObjectMapper().writeValue(response.getOutputStream(), tokens);
			} catch (Exception exception) {
				response.setHeader("error", exception.getMessage());
				response.setStatus(HttpStatus.FORBIDDEN.value());

				Map<String, String> error = new HashMap<>();
				error.put("error_message", exception.getMessage());
				response.setContentType(MediaType.APPLICATION_JSON_VALUE);
				new ObjectMapper().writeValue(response.getOutputStream(), error);
			}
		} else {
			throw new RuntimeException("Refresh token is missing"); 
		}
	}
	
}
