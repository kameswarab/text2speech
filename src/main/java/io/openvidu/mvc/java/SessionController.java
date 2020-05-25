package io.openvidu.mvc.java;

import java.io.BufferedInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.net.URL;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import javax.servlet.http.HttpSession;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RequestParam;

import io.openvidu.java.client.OpenVidu;
import io.openvidu.java.client.OpenViduRole;
import io.openvidu.java.client.Recording;
import io.openvidu.java.client.Recording.OutputMode;
import io.openvidu.java.client.RecordingMode;
import io.openvidu.java.client.Session;
import io.openvidu.java.client.SessionProperties;
import io.openvidu.java.client.TokenOptions;

@Controller
public class SessionController {

	// OpenVidu object as entrypoint of the SDK
	private OpenVidu openVidu;

	// Collection to pair session names and OpenVidu Session objects
	private Map<String, Session> mapSessions = new ConcurrentHashMap<>();
	// Collection to pair session names and tokens (the inner Map pairs tokens and role associated)
	private Map<String, Map<String, OpenViduRole>> mapSessionNamesTokens = new ConcurrentHashMap<>();

	// URL where our OpenVidu server is listening
	private String OPENVIDU_URL;
	// Secret shared with our OpenVidu server
	private String SECRET;

	public SessionController(@Value("${openvidu.secret}") String secret, @Value("${openvidu.url}") String openviduUrl) {
		this.SECRET = secret;
		this.OPENVIDU_URL = openviduUrl;
		this.openVidu = new OpenVidu(OPENVIDU_URL, SECRET);
	}

	@RequestMapping(value = "/session", method = RequestMethod.POST)
	public String joinSession(@RequestParam(name = "data") String clientData,
			@RequestParam(name = "session-name") String sessionName, Model model, HttpSession httpSession) {

		try {
			checkUserLogged(httpSession);
		} catch (Exception e) {
			
			return "index";
		}
		System.out.println("Getting sessionId and token | {sessionName}={" + sessionName + "}");

		// Role associated to this user
		OpenViduRole role = LoginController.users.get(httpSession.getAttribute("loggedUser")).role;

		// Optional data to be passed to other users when this user connects to the video-call
		// In this case, a JSON with the value we stored in the HttpSession object on login
		String serverData = "{\"serverData\": \"" + httpSession.getAttribute("loggedUser") + "\"}";

		// Build tokenOptions object with the serverData and the role
		TokenOptions tokenOptions = new TokenOptions.Builder().data(serverData).role(role).build();

		if (this.mapSessions.get(sessionName) != null) {
			// Session already exists
			System.out.println("Existing session " + sessionName);
			try {

				// Generate a new token with the recently created tokenOptions
				String token = this.mapSessions.get(sessionName).generateToken(tokenOptions);

				// Update our collection storing the new token
				this.mapSessionNamesTokens.get(sessionName).put(token, role);

				// Add all the needed attributes to the template
				model.addAttribute("sessionName", sessionName);
				model.addAttribute("token", token);
				model.addAttribute("nickName", clientData);
				model.addAttribute("userName", httpSession.getAttribute("loggedUser"));

				// Return session.html template
				return "session";

			} catch (Exception e) {
				// If error just return dashboard.html template
				model.addAttribute("username", httpSession.getAttribute("loggedUser"));
				return "dashboard";
			}
		} else {
			// New session
			System.out.println("New session " + sessionName);
			try {

				SessionProperties properties = new SessionProperties.Builder()
					    .recordingMode(RecordingMode.ALWAYS) // RecordingMode.ALWAYS for automatic recording
					    .defaultOutputMode(OutputMode.COMPOSED)
					    .build();
				// Create a new OpenVidu Session
				Session session = this.openVidu.createSession(properties);
				// Generate a new token with the recently created tokenOptions
				String token = session.generateToken(tokenOptions);

				// Store the session and the token in our collections
				this.mapSessions.put(sessionName, session);
				this.mapSessionNamesTokens.put(sessionName, new ConcurrentHashMap<>());
				this.mapSessionNamesTokens.get(sessionName).put(token, role);

				// Add all the needed attributes to the template
				model.addAttribute("sessionName", sessionName);
				model.addAttribute("token", token);
				model.addAttribute("nickName", clientData);
				model.addAttribute("userName", httpSession.getAttribute("loggedUser"));

				// Return session.html template
				return "session";

			} catch (Exception e) {
				// If error just return dashboard.html template
				model.addAttribute("username", httpSession.getAttribute("loggedUser"));
				return "dashboard";
			}
		}
	}

	@RequestMapping(value = "/leave-session", method = RequestMethod.POST)
	public String removeUser(@RequestParam(name = "session-name") String sessionName,
			@RequestParam(name = "token") String token, Model model, HttpSession httpSession) throws Exception {

		try {
			checkUserLogged(httpSession);
		} catch (Exception e) {
			return "index";
		}
		System.out.println("Removing user | sessioName=" + sessionName + ", token=" + token);

		// If the session exists ("TUTORIAL" in this case)
		if (this.mapSessions.get(sessionName) != null && this.mapSessionNamesTokens.get(sessionName) != null) {

			// If the token exists
			if (this.mapSessionNamesTokens.get(sessionName).remove(token) != null) {
				// User left the session
				if (this.mapSessionNamesTokens.get(sessionName).isEmpty()) {
					// Last user left: session must be removed
					this.mapSessions.remove(sessionName);
				}
				return "redirect:/dashboard";

			} else {
				// The TOKEN wasn't valid
				System.out.println("Problems in the app server: the TOKEN wasn't valid");
				return "redirect:/dashboard";
			}

		} else {
			// The SESSION does not exist
			System.out.println("Problems in the app server: the SESSION does not exist");
			return "redirect:/dashboard";
		}
	}

	@RequestMapping(value = "/getrecordings", method = RequestMethod.GET)
	public String getrecordings()  throws Exception {

		try {
			List<Recording> recordingList = openVidu.listRecordings();
			/*
			 * Recording r = recordingList.get(0); String url = r.getUrl();
			 * System.out.println(url);
			 */
			int i=0;
			for(Recording r : recordingList) {
				System.out.println(r.getName());
				System.out.println(r.getUrl());
				String url = r.getUrl().replace("https", "http");
				String[] ss = url.split("\\.");
				System.out.println(ss[1]);
				try (BufferedInputStream inputStream = new BufferedInputStream(new URL(url).openStream());
						  FileOutputStream fileOS = new FileOutputStream("C:\\Uploads\\Test\\mp3\\downloaded\\file_name"+(i++)+ss[1])) {
						    byte data[] = new byte[1024];
						    int byteContent;
						    while ((byteContent = inputStream.read(data, 0, 1024)) != -1) {
						        fileOS.write(data, 0, byteContent);
						    }
						} catch (IOException e) {
						    e.printStackTrace();
						}	
			}
			
		} catch (Exception e) {
			e.printStackTrace();
			return "index";
		}
		
			return "redirect:/dashboard";
		}
	

	
	private void checkUserLogged(HttpSession httpSession) throws Exception {
		if (httpSession == null || httpSession.getAttribute("loggedUser") == null) {
			throw new Exception("User not logged");
		}
	}

}
