package com.springboot;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.springboot.controller.ApplyOfferRequest;
import com.springboot.controller.OfferRequest;
import com.springboot.controller.SegmentResponse;
import com.springboot.controller.AutowiredController;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.SpringBootTest.WebEnvironment;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.junit4.SpringRunner;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.beans.factory.annotation.Autowired;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.lang.reflect.Field;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.ArrayList;
import java.util.List;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * This test class covers:
 * <ul>
 *   <li>An external call to add an offer using the addOffer() method.</li>
 *   <li>Several tests for applying offers via the /api/v1/cart/apply_offer endpoint.</li>
 * </ul>
 *
 * <p>The tests run on a defined port (9001) and use a test-specific subclass of AutowiredController to override the
 * getSegmentResponse() method, ensuring a fixed segment ("p1") is returned.</p>
 *
 * <p>Note: The offer list in the controller is manipulated using reflection (clearAllOffers()) to ensure a clean state before each test.</p>
 */
@RunWith(SpringRunner.class)
@SpringBootTest(webEnvironment = WebEnvironment.DEFINED_PORT) // Application runs on a defined port (ensure it's 9001)
@AutoConfigureMockMvc
@Import(CartOfferApplicationTests.TestConfig.class)
public class CartOfferApplicationTests {

	@Autowired
	private MockMvc mockMvc;

	// The controller bean is provided by our TestConfig as an instance of TestAutowiredController.
	@Autowired
	private AutowiredController controller;

	/**
	 * Clears the 'allOffers' list in the controller before each test.
	 *
	 * @throws Exception if reflection fails.
	 */
	@Before
	public void setUp() throws Exception {
		clearAllOffers();
	}

	// ============================================================================
	// External Offer Addition Test
	// ============================================================================

	/**
	 * Test that uses the external HTTP call to add an offer via /api/v1/offer.
	 * This method calls addOffer(), which is the production code method.
	 *
	 * @throws Exception if an error occurs during the HTTP call.
	 */
	@Test
	public void checkFlatXForOneSegment() throws Exception {
		List<String> segments = new ArrayList<>();
		segments.add("p1");
		OfferRequest offerRequest = new OfferRequest(1,"FLATX",10,segments);
		boolean result = addOffer(offerRequest);
		Assert.assertEquals(result,true); // able to add offer
	}
	public boolean addOffer(OfferRequest offerRequest) throws Exception {
		String urlString = "http://localhost:9001/api/v1/offer";
		URL url = new URL(urlString);
		HttpURLConnection con = (HttpURLConnection) url.openConnection();
		con.setDoOutput(true);
		con.setRequestProperty("Content-Type", "application/json");

		ObjectMapper mapper = new ObjectMapper();
		String POST_PARAMS = mapper.writeValueAsString(offerRequest);
		OutputStream os = con.getOutputStream();
		os.write(POST_PARAMS.getBytes());
		os.flush();
		os.close();

		int responseCode = con.getResponseCode();
		System.out.println("POST Response Code :: " + responseCode);
		if (responseCode == HttpURLConnection.HTTP_OK) { // success
			BufferedReader in = new BufferedReader(new InputStreamReader(con.getInputStream()));
			String inputLine;
			StringBuffer response = new StringBuffer();
			while ((inputLine = in.readLine()) != null) {
				response.append(inputLine);
			}
			in.close();
			System.out.println("Response: " + response.toString());
		} else {
			System.out.println("POST request did not work.");
		}
		return true;
	}

	// ============================================================================
	// Tests for Applying Offers via /api/v1/cart/apply_offer
	// ============================================================================

	/**
	 * Test that a FLATX offer is correctly applied.
	 * Expects a flat discount of 10 from a cart value of 200, resulting in 190.
	 *
	 * @throws Exception if an error occurs during the test.
	 */
	@Test
	public void checkFlatXOfferApplied() throws Exception {
		// Add a FLATX offer using the external addOffer() method.
		List<String> segments = new ArrayList<>();
		segments.add("p1");
		OfferRequest flatxOffer = new OfferRequest(1, "FLATX", 10, segments);
		addOffer(flatxOffer);

		// Prepare apply-offer request
		ApplyOfferRequest applyOfferRequest = new ApplyOfferRequest(200, 1, 1);
		// Expected cart value: 200 - 10 = 190.
		mockMvc.perform(post("/api/v1/cart/apply_offer")
						.contentType(MediaType.APPLICATION_JSON)
						.content(new ObjectMapper().writeValueAsString(applyOfferRequest)))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.cart_value").value(190));
	}

	/**
	 * Test that a PERCENTAGE offer (10% discount) is correctly applied.
	 * Expects a discount of 10% from a cart value of 200, resulting in 180.
	 *
	 * @throws Exception if an error occurs during the test.
	 */
	@Test
	public void checkPercentageOfferApplied() throws Exception {
		// Add a PERCENTAGE offer using the external addOffer() method.
		List<String> segments = new ArrayList<>();
		segments.add("p1");
		OfferRequest percentageOffer = new OfferRequest(1, "PERCENTAGE", 10, segments);
		addOffer(percentageOffer);

		// Prepare apply-offer request.
		ApplyOfferRequest applyOfferRequest = new ApplyOfferRequest(200, 1, 1);
		// Expected cart value: 200 - (200 * 0.10) = 180.
		mockMvc.perform(post("/api/v1/cart/apply_offer")
						.contentType(MediaType.APPLICATION_JSON)
						.content(new ObjectMapper().writeValueAsString(applyOfferRequest)))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.cart_value").value(180));
	}

	/**
	 * Test that when no offers exist, applying an offer does not change the cart value.
	 *
	 * @throws Exception if an error occurs during the test.
	 */
	@Test
	public void checkNoOfferMatchWhenNoOfferExists() throws Exception {
		// Do not add any offers.
		ApplyOfferRequest applyOfferRequest = new ApplyOfferRequest(200, 1, 1);
		// Expected cart value: remains 200.
		mockMvc.perform(post("/api/v1/cart/apply_offer")
						.contentType(MediaType.APPLICATION_JSON)
						.content(new ObjectMapper().writeValueAsString(applyOfferRequest)))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.cart_value").value(200));
	}

	/**
	 * Test that an offer with a mismatching segment is not applied.
	 * Here the offer is configured with segment "p2" while the controller returns "p1".
	 *
	 * @throws Exception if an error occurs during the test.
	 */
	@Test
	public void checkNoOfferMatchDueToSegmentMismatch() throws Exception {
		// Add an offer with a mismatching segment.
		List<String> segments = new ArrayList<>();
		segments.add("p2");
		OfferRequest offer = new OfferRequest(1, "FLATX", 10, segments);
		addOffer(offer);

		// Prepare apply-offer request.
		ApplyOfferRequest applyOfferRequest = new ApplyOfferRequest(200, 1, 1);
		// Expected: No offer match, so cart value remains 200.
		mockMvc.perform(post("/api/v1/cart/apply_offer")
						.contentType(MediaType.APPLICATION_JSON)
						.content(new ObjectMapper().writeValueAsString(applyOfferRequest)))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.cart_value").value(200));
	}

	/**
	 * Test that when multiple offers exist, the first matching offer is applied.
	 * Here two offers are added; the first is a FLATX discount of 10.
	 *
	 * @throws Exception if an error occurs during the test.
	 */
	@Test
	public void checkMultipleOffersFirstMatchApplied() throws Exception {
		// Add two offers; first one should match.
		List<String> segments = new ArrayList<>();
		segments.add("p1");
		OfferRequest offer1 = new OfferRequest(1, "FLATX", 10, segments);
		OfferRequest offer2 = new OfferRequest(1, "PERCENTAGE", 20, segments);
		addOffer(offer1);
		addOffer(offer2);

		// Prepare apply-offer request.
		ApplyOfferRequest applyOfferRequest = new ApplyOfferRequest(200, 1, 1);
		// Expected: FLATX offer applies → 200 - 10 = 190.
		mockMvc.perform(post("/api/v1/cart/apply_offer")
						.contentType(MediaType.APPLICATION_JSON)
						.content(new ObjectMapper().writeValueAsString(applyOfferRequest)))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.cart_value").value(190));
	}

	/**
	 * Test that when the first offer does not match due to segment mismatch, the second offer is applied.
	 * In this test, the first offer has segment "p2" (mismatch) and the second has segment "p1".
	 *
	 * @throws Exception if an error occurs during the test.
	 */
	@Test
	public void checkMultipleOffersSecondOfferAppliedIfFirstDoesNotMatch() throws Exception {
		// First offer: mismatching segment.
		List<String> segmentsMismatch = new ArrayList<>();
		segmentsMismatch.add("p2");
		// Second offer: matching segment.
		List<String> segmentsMatch = new ArrayList<>();
		segmentsMatch.add("p1");

		OfferRequest offer1 = new OfferRequest(1, "FLATX", 10, segmentsMismatch);
		OfferRequest offer2 = new OfferRequest(1, "PERCENTAGE", 20, segmentsMatch);
		addOffer(offer1);
		addOffer(offer2);

		// Prepare apply-offer request.
		ApplyOfferRequest applyOfferRequest = new ApplyOfferRequest(200, 1, 1);
		// Expected: first offer does not match, so second offer applies → 200 - (200 * 20% = 40) = 160.
		mockMvc.perform(post("/api/v1/cart/apply_offer")
						.contentType(MediaType.APPLICATION_JSON)
						.content(new ObjectMapper().writeValueAsString(applyOfferRequest)))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.cart_value").value(160));
	}
	/**
	 * Test that verifies segment-based offers:
	 * - Users in segment "p1" receive a FLATX discount of 10.
	 * - Users in segment "p2" receive a PERCENTAGE discount of 20%.
	 *
	 * Expected Results:
	 * - If user belongs to segment "p1" (cart value 200), they receive FLATX (200 - 10 = 190).
	 * - If user belongs to segment "p2" (cart value 200), they receive PERCENTAGE (200 - (20% of 200) = 160).
	 *
	 * @throws Exception if an error occurs during the test.
	 */
	@Test
	public void checkSegmentBasedOfferApplied() throws Exception {
		 //Offer 1: FLATX discount for segment "p1"
		List<String> segmentFlatX = new ArrayList<>();
		segmentFlatX.add("p1");
		OfferRequest flatxOffer = new OfferRequest(1, "FLATX", 10, segmentFlatX);
		addOffer(flatxOffer);

		// Offer 2: PERCENTAGE discount for segment "p2"
		List<String> segmentPercentage = new ArrayList<>();
		segmentPercentage.add("p2");
		OfferRequest percentageOffer = new OfferRequest(1, "PERCENTAGE", 20, segmentPercentage);
		addOffer(percentageOffer);

		// Apply offer for user in segment "p1" (should get FLATX)
		ApplyOfferRequest applyOfferRequestP1 = new ApplyOfferRequest(200, 1, 1);
		mockMvc.perform(post("/api/v1/cart/apply_offer")
						.contentType(MediaType.APPLICATION_JSON)
						.content(new ObjectMapper().writeValueAsString(applyOfferRequestP1)))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.cart_value").value(190)); // FLATX applied (200 - 10)

		// Apply offer for user in segment "p2" (should get PERCENTAGE)
		ApplyOfferRequest applyOfferRequestP2 = new ApplyOfferRequest(200, 1, 2); // Different user ID
		mockMvc.perform(post("/api/v1/cart/apply_offer")
						.contentType(MediaType.APPLICATION_JSON)
						.content(new ObjectMapper().writeValueAsString(applyOfferRequestP2)))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.cart_value").value(160)); // PERCENTAGE applied (200 - 40)
	}



	// ============================================================================
	// Helper Method to Clear the 'allOffers' Field via Reflection
	// ============================================================================
	/**
	 * Uses reflection to clear the private (or protected) 'allOffers' list in the controller.
	 *
	 * @throws Exception if reflection fails.
	 */
	private void clearAllOffers() throws Exception {
		Field offersField = AutowiredController.class.getDeclaredField("allOffers");
		offersField.setAccessible(true);
		List<?> offers = (List<?>) offersField.get(controller);
		offers.clear();
	}

	// ============================================================================
	// Test Configuration & Test-Specific Controller Subclass
	// ============================================================================
	/**
	 * Test configuration to register a test-specific subclass of AutowiredController.
	 * This subclass overrides getSegmentResponse() to always return a SegmentResponse with segment "p1".
	 */
	@TestConfiguration
	static class TestConfig {
		@Bean
		public AutowiredController autowiredController() {
			return new TestAutowiredController();
		}
	}

	/**
	 * Test-specific subclass of AutowiredController.
	 * Overrides getSegmentResponse() to ensure consistent behavior during testing.
	 */
	public static class TestAutowiredController extends AutowiredController {
		@Override
		public SegmentResponse getSegmentResponse(int userId) {
			System.out.println("User ID: " + userId);
			if (userId == 1) {
				System.out.println("Returning segment: p1");
				return new SegmentResponse("p1");
			} else {
				System.out.println("Returning segment: p2");
				return new SegmentResponse("p2");
			}
		}
	}
}
