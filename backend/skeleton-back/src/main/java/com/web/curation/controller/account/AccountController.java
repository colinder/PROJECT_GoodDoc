package com.web.curation.controller.account;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.Map;
import java.util.Optional;
import java.util.Random;

import javax.validation.Valid;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.scheduling.annotation.Async;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.web.curation.dao.user.UserDao;
import com.web.curation.model.BasicResponse;
import com.web.curation.model.user.ChangepwdRequest;
import com.web.curation.model.user.SignupRequest;
import com.web.curation.model.user.User;

import io.swagger.annotations.ApiOperation;
import io.swagger.annotations.ApiResponse;
import io.swagger.annotations.ApiResponses;
import springfox.documentation.swagger2.annotations.EnableSwagger2;

@ApiResponses(value = { @ApiResponse(code = 401, message = "Unauthorized", response = BasicResponse.class),
		@ApiResponse(code = 403, message = "Forbidden", response = BasicResponse.class),
		@ApiResponse(code = 404, message = "Not Found", response = BasicResponse.class),
		@ApiResponse(code = 500, message = "Failure", response = BasicResponse.class) })
@EnableSwagger2
//@CrossOrigin(origins = { "http://localhost:3000" }) //이쪽에 있는 내용만 받아온다는것.
@CrossOrigin(origins = { "*" })
@RestController
public class AccountController {

	@Autowired
	UserDao userDao;
	@Autowired
	public JavaMailSender javaMailSender;

	@GetMapping("/account/gooddoc")
	@ApiOperation(value = "로그인")
	public Object login(@RequestParam(required = true) final String email,
			@RequestParam(required = true) final String password) {
		
		Optional<User> userOpt = userDao.findUserByEmailAndPassword(email, password);
		
		ResponseEntity response = null;
		if (userOpt.isPresent()) {
			User user = new User();
			user = userOpt.get();
			response = new ResponseEntity<>(user, HttpStatus.OK);
		} else {
			response = new ResponseEntity<>(null, HttpStatus.NOT_FOUND);
		}

		return response;
	}

	@PostMapping("/account")
	@ApiOperation(value = "가입하기")
	public Object signup(@Valid @RequestBody SignupRequest request) {
		if (userDao.getUserByEmail(request.getEmail()) != null) {
			final BasicResponse result = new BasicResponse();
			result.status = true;
			result.data = "email_fail";
			return new ResponseEntity<>(result, HttpStatus.NOT_FOUND);
		} else if (userDao.getUserByNickname(request.getNickname()) != null) {
			final BasicResponse result = new BasicResponse();
			result.status = true;
			result.data = "nickname_fail";
			return new ResponseEntity<>(result, HttpStatus.NOT_FOUND);
		}
		User user = new User();
		user.setNickname(request.getNickname());
		user.setEmail(request.getEmail());
		user.setPassword(request.getPassword());
		user.setAccountType(0);
		User temp = userDao.save(user);

		final BasicResponse result = new BasicResponse();
		result.status = true;
		result.data = "success";

		return new ResponseEntity<>(result, HttpStatus.OK);
	}

	@PutMapping("/pwd")
	@ApiOperation(value = "비밀번호변경")
	public Object changepwd(@Valid @RequestBody ChangepwdRequest request) {
		BasicResponse result = null;
		Optional<User> userOpt = userDao.findUserByEmailAndPassword(request.getEmail(), request.getOldPassword());

		if (!userOpt.isPresent()) { // 존재하지 않는 경우
			result = new BasicResponse();
			result.status = true;
			result.data = "fail";
			return new ResponseEntity<>(result, HttpStatus.NOT_FOUND);
		} else { // 존재하는 아이디 비번일 경우
			User user = userOpt.get();
			user.setPassword(request.getNewPassword());
			userDao.save(user);
			result = new BasicResponse();
			result.status = true;
			result.data = "success";
			return new ResponseEntity<>(result, HttpStatus.OK);
		}
	}

	@PostMapping(value = "/pwd")
	   @Async
	   @ApiOperation(value = "비밀번호 찾기")
	   public Object sendMailToFindPwd(@Valid @RequestBody Map<String, String> data) {
	      SimpleMailMessage simpleMessage = new SimpleMailMessage();

	      String email = data.get("email");
	      User user = userDao.getUserByEmail(email);
	      simpleMessage.setTo(email);
	      simpleMessage.setSubject(email + "님에 대한 비밀번호 찾기 결과입니다");
	      simpleMessage.setText(email + "님에 대한 비밀번호는 " + user.getPassword());
	      if(user.getAccountType()==0)
	      javaMailSender.send(simpleMessage);
	      
	      System.out.println(user);
	      return new ResponseEntity<>(user, HttpStatus.OK);
	   }
	
	// 이메일 인증
	@PostMapping(value = "/email")
	@Async
	@ApiOperation(value = "이메일인증")
	public ResponseEntity<BasicResponse> sendMail(@Valid @RequestBody Map<String, String> data) {
		SimpleMailMessage simpleMessage = new SimpleMailMessage();

		// 이메일 인증
		int random = new Random().nextInt(900000) + 100000;
		String authCode = String.valueOf(random);

		simpleMessage.setTo(data.get("email"));
		simpleMessage.setSubject("이메일 인증");
		simpleMessage.setText("인증번호: " + authCode);
		javaMailSender.send(simpleMessage);

		// 추가로 뷰에 autoCode저장 + 확인필요
		final BasicResponse result = new BasicResponse();
		result.status = true;
		result.data = "success";
		result.object = authCode;

		return new ResponseEntity<>(result, HttpStatus.OK);
	}

	// 구글로그인인지 + 이메일이 있는지 체크해서 있으면 로그인시켜주고 없으면 회원가입!
	   @PostMapping("/account/google")
	   @Async
	   @ApiOperation(value = "구글로그인")
	   public Object gLogin(@Valid @RequestBody Map<String, String> data) {
	      final BasicResponse result = new BasicResponse();
	      String gEmail = data.get("gEmail");
	      String gNickname = data.get("gNickname");
	      User user = new User();
	      user.setAccountType(1);
	      user.setEmail(gEmail);
	      user.setNickname(gNickname);
	      user.setPassword("");

	      if (userDao.getUserByEmailAndAccountType(gEmail, 1) == null) {
	         userDao.save(user);
	      } else {
	         user = userDao.getUserByEmailAndAccountType(gEmail, 1);
	      }

	      return new ResponseEntity<>(user, HttpStatus.OK);
	   }

	   @PostMapping("/account/kakao")
	   @Async
	   @ApiOperation(value = "카카오 로그인")
	   public Object kLogin(@Valid @RequestBody Map<String, String> data) {

	      String email = data.get("email");
	      String nickname = data.get("nickname");
	      User user = new User();
	      user.setAccountType(2);
	      user.setEmail(email);
	      user.setNickname(nickname);
	      user.setPassword("");

	      if (userDao.getUserByEmailAndAccountType(email, 2) == null) {
	         userDao.save(user);
	      } else {
	         user = userDao.getUserByEmailAndAccountType(email, 2);
	      }
	      return new ResponseEntity<>(user, HttpStatus.OK);
	   }
}