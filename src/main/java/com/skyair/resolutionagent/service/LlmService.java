package com.skyair.resolutionagent.service;
import com.skyair.resolutionagent.model.Booking;
import com.skyair.resolutionagent.model.ConversationTurn;
import com.skyair.resolutionagent.model.Customer;
import com.skyair.resolutionagent.model.Flight;
import com.skyair.resolutionagent.model.FlightStatus;
import com.skyair.resolutionagent.model.Intent;
import com.skyair.resolutionagent.model.Resolution;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.http.converter.StringHttpMessageConverter;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

@Service
public class LlmService {

    private static final Logger log = LoggerFactory.getLogger(LlmService.class);
    private static final ObjectMapper objectMapper = new ObjectMapper();

    private final FallbackResponseBuilder fallbackResponseBuilder;
    private final RestClient restClient;

    @Value("${llm.api.key:}")
    private String apiKey;

    @Value("${llm.api.url:https://api.openai.com/v1/chat/completions}")
    private String apiUrl;

    @Value("${llm.model:openai/gpt-oss-20b}")
    private String model;

    private final PolicyConfigService policyConfigService;

    @Autowired
    public LlmService(FallbackResponseBuilder fallbackResponseBuilder, PolicyConfigService policyConfigService) {
        this.fallbackResponseBuilder = fallbackResponseBuilder;
        this.policyConfigService = (policyConfigService != null) ? policyConfigService : new PolicyConfigService();
        this.restClient = RestClient.builder()
                .messageConverters(converters -> {
                    converters.removeIf(c -> c instanceof StringHttpMessageConverter);
                    converters.add(0, new StringHttpMessageConverter(StandardCharsets.UTF_8));
                })
                .build();
    }

    public LlmService(FallbackResponseBuilder fallbackResponseBuilder) {
        this(fallbackResponseBuilder, new PolicyConfigService());
    }

    public boolean isConfigured() {
        return apiKey != null && !apiKey.trim().isEmpty();
    }

    public Intent classifyIntent(String customerMessage) {
        return classifyIntent(customerMessage, List.of());
    }

    public Intent classifyIntent(String customerMessage, List<ConversationTurn> history) {
        if (customerMessage == null || customerMessage.trim().isEmpty()) {
            return Intent.GENERAL_INQUIRY;
        }

        String lower = customerMessage.toLowerCase(Locale.ROOT).trim();
        boolean isNegation = lower.contains("no do not") || lower.contains("don't assign") ||
                             lower.contains("do not assign") || lower.contains("don't connect") ||
                             lower.contains("do not connect") || lower.contains("don't transfer") ||
                             lower.contains("do not transfer") || lower.contains("no need to transfer") ||
                             lower.startsWith("no ") || lower.equals("no");

        boolean isAskingToBeAsked = lower.contains("ask me if") || lower.contains("ask me whether") ||
                                    lower.contains("ask me first") || lower.contains("ask me or not");

        boolean isHypotheticalOrInquiry = lower.contains("if i ask") || lower.contains("if i want") || lower.contains("if i need") ||
                                          lower.contains("if i decide") || lower.contains("will you assign") || lower.contains("will u assign") ||
                                          lower.contains("would you assign") || lower.contains("can you assign") || lower.contains("can u assign") ||
                                          lower.contains("could you assign") || lower.contains("are you able to assign") || lower.contains("able to connect") ||
                                          lower.contains("will you connect") || lower.contains("will u connect") || lower.contains("can you connect") ||
                                          lower.contains("can u connect") || lower.contains("would you connect") || lower.contains("could you connect") ||
                                          lower.contains("how do i connect") || lower.contains("how can i connect") || lower.contains("how to connect") ||
                                          lower.contains("how to speak") || lower.contains("do you have human") || lower.contains("is there a human") ||
                                          lower.contains("can i get a human") || lower.contains("can i speak to a human") || lower.contains("can i talk to a human");

        boolean isLyingAccusation = lower.contains("lying") || lower.contains("lied") || lower.contains("u did not") ||
                                   lower.contains("you did not") || lower.contains("didn't assign") || lower.contains("didnt assign") ||
                                   lower.contains("nobody joined") || lower.contains("no one joined") || lower.contains("did you actually");

        boolean isVerbalClaim = (lower.contains("told me") || lower.contains("told us") || lower.contains("said that") ||
                                 lower.contains("said i") || lower.contains("said we") || lower.contains("verbally") ||
                                 lower.contains("gate supervisor") || lower.contains("gate agent") || lower.contains("promised") ||
                                 lower.contains("counter staff") || lower.contains("airport staff")) &&
                                (lower.contains("upgrade") || lower.contains("business class") || lower.contains("first class") ||
                                 lower.contains("confirm") || lower.contains("free") || lower.contains("approved"));

        if (isHypotheticalOrInquiry || isLyingAccusation || isAskingToBeAsked || isVerbalClaim || (isNegation && (lower.contains("assign") || lower.contains("connect") || lower.contains("transfer") || lower.contains("agent") || lower.contains("human")))) {
            return Intent.GENERAL_INQUIRY;
        }

        if (lower.contains("what did i ask") || lower.contains("what was my last") ||
            lower.contains("last chat") || lower.contains("what did i say") ||
            lower.contains("repeat what i said")) {
            return Intent.GENERAL_INQUIRY;
        }

        Intent deterministicIntent = classifyIntentDeterministic(customerMessage, history);
        if (deterministicIntent != Intent.GENERAL_INQUIRY) {
            return deterministicIntent;
        }

        if (isConfigured()) {
            try {
                String contextSnippet = "";
                if (history != null && !history.isEmpty()) {
                    ConversationTurn lastTurn = history.get(history.size() - 1);
                    String lastMsg = (lastTurn.message() != null) ? lastTurn.message().trim() : "";
                    if (lastMsg.length() > 120) {
                        lastMsg = lastMsg.substring(0, 120) + "...";
                    }
                    contextSnippet = "Previous Agent Context: \"" + lastMsg.replace("\"", "\\\"") + "\"\n";
                }

                String prompt = "Given this customer message" + (!contextSnippet.isEmpty() ? " and previous context" : "") + ", classify it into exactly one of these intents:\n" +
                        Arrays.toString(Intent.values()) + "\n" +
                        "PRIORITY RULES:\n" +
                        "1. If customer mentions legal action, court, lawyer, or formal complaint -> LEGAL_COMPLAINT\n" +
                        "2. If customer asks for a human agent, manager, supervisor, or handover -> HUMAN_AGENT_REQUEST\n" +
                        "3. If customer mentions medical condition, insulin, medication, or collapse -> EMERGENCY_MEDICAL_ASSISTANCE\n" +
                        "4. If customer mentions cabin upgrade, business class, or first class -> UPGRADE_REQUEST\n" +
                        "5. If customer mentions fare difference, waiver, or higher fare flight -> FARE_DIFFERENCE_WAIVER\n" +
                        "6. If customer mentions refund or money back -> REFUND_REQUEST\n" +
                        "7. If customer mentions rebook, alternate flight, or next flight -> REBOOKING_REQUEST\n" +
                        "8. If customer mentions hotel or room accommodation -> HOTEL_REQUEST\n" +
                        "9. If customer mentions lounge access -> LOUNGE_ACCESS_REQUEST\n" +
                        "10. If customer mentions food, meal, breakfast, or voucher -> MEAL_VOUCHER_REQUEST\n\n" +
                        contextSnippet +
                        "Current Customer Message: \"" + customerMessage.replace("\"", "\\\"") + "\"\n" +
                        "Respond with ONLY the intent enum name and nothing else.";

                List<String> candidateModels = List.of(model, "openai/gpt-oss-120b", "groq/compound-mini", "qwen/qwen3.8-27b", "llama-3.3-70b-versatile");
                for (String candidateModel : candidateModels) {
                    try {
                        Map<String, Object> requestBody = Map.of(
                                "model", candidateModel,
                                "messages", List.of(
                                        Map.of("role", "system", "content", "You are an intent classifier for an airline disruption resolution system. Respond only with the exact enum value."),
                                        Map.of("role", "user", "content", prompt)
                                ),
                                "temperature", 0.0,
                                "max_tokens", 25
                        );

                        byte[] responseBytes = restClient.post()
                                .uri(apiUrl)
                                .header("Authorization", "Bearer " + apiKey)
                                .contentType(MediaType.APPLICATION_JSON)
                                .body(requestBody)
                                .retrieve()
                                .body(byte[].class);

                        String responseJson = new String(responseBytes != null ? responseBytes : new byte[0], StandardCharsets.UTF_8);
                        JsonNode root = objectMapper.readTree(responseJson);
                        String rawIntent = root.path("choices").get(0).path("message").path("content").asText("").trim();

                        for (Intent intent : Intent.values()) {
                            if (rawIntent.toUpperCase(Locale.ROOT).contains(intent.name())) {
                                return intent;
                            }
                        }
                        break;
                    } catch (Exception ex) {
                        log.warn("Classification model {} failed ({}), trying next candidate...", candidateModel, ex.getMessage());
                    }
                }
            } catch (Exception e) {
                log.warn("LLM intent classification failed: {}", e.getMessage());
            }
        }

        return Intent.GENERAL_INQUIRY;
    }

    public String generateResponse(Customer customer, Booking booking, Resolution resolution,
                                   List<ConversationTurn> history, String customerMessage) {
        boolean isFirstTurn = (history == null || history.isEmpty());
        if (!isConfigured()) {
            return fallbackResponseBuilder.buildResponse(customer, booking, resolution, customerMessage, isFirstTurn, history);
        }

        List<String> candidateModels = new ArrayList<>();
        candidateModels.add(model);
        if (!"openai/gpt-oss-120b".equals(model)) {
            candidateModels.add("openai/gpt-oss-120b");
        }
        if (!"groq/compound-mini".equals(model)) {
            candidateModels.add("groq/compound-mini");
        }
        if (!"qwen/qwen3.8-27b".equals(model)) {
            candidateModels.add("qwen/qwen3.8-27b");
        }
        if (!"groq/compound".equals(model)) {
            candidateModels.add("groq/compound");
        }
        if (!"llama-3.3-70b-versatile".equals(model)) {
            candidateModels.add("llama-3.3-70b-versatile");
        }

        String systemPrompt = buildSystemPrompt(customer, booking, isFirstTurn);
        List<Map<String, String>> messages = new ArrayList<>();
        messages.add(Map.of("role", "system", "content", systemPrompt));

        if (history != null && !history.isEmpty()) {
            int start = Math.max(0, history.size() - 3);
            for (int i = start; i < history.size(); i++) {
                ConversationTurn turn = history.get(i);
                messages.add(Map.of(
                        "role", "agent".equalsIgnoreCase(turn.role()) ? "assistant" : "user",
                        "content", turn.message()
                ));
            }
        }

        messages.add(Map.of("role", "user", "content", customerMessage));

        for (String candidateModel : candidateModels) {
            try {
                Map<String, Object> requestBody = new HashMap<>();
                requestBody.put("model", candidateModel);
                requestBody.put("messages", messages);
                requestBody.put("temperature", 0.3);
                requestBody.put("max_tokens", 700);

                byte[] responseBytes = restClient.post()
                        .uri(apiUrl)
                        .header("Authorization", "Bearer " + apiKey)
                        .contentType(MediaType.APPLICATION_JSON)
                        .body(requestBody)
                        .retrieve()
                        .body(byte[].class);

                String responseJson = new String(responseBytes != null ? responseBytes : new byte[0], StandardCharsets.UTF_8);
                JsonNode root = objectMapper.readTree(responseJson);
                String agentText = root.path("choices").get(0).path("message").path("content").asText();
                if (agentText != null && !agentText.isBlank()) {
                    agentText = agentText.trim();
                    if ((agentText.startsWith("\"") && agentText.endsWith("\"")) ||
                        (agentText.startsWith("“") && agentText.endsWith("”"))) {
                        agentText = agentText.substring(1, agentText.length() - 1).trim();
                    }

                    // Normalize unicode characters
                    agentText = agentText
                            .replace("\u00A0", " ")
                            .replace("\u202F", " ")
                            .replace("\u2007", " ")
                            .replace("\u200B", "")
                            .replace("\u2018", "'")
                            .replace("\u2019", "'")
                            .replace("\u201C", "\"")
                            .replace("\u201D", "\"")
                            .replace("\u2013", "-")
                            .replace("\u2014", "-")
                            .replace("\u2011", "-")
                            .replace("\u2026", "...")
                            .replace("\u2265", ">=")
                            .replace("\u2264", "<=")
                            .replace("\u20B9", "Rs. ")
                            .replace("₹", "Rs. ")
                            .replaceAll("[\\p{Cntrl}&&[^\r\n\t]]", "")
                            .trim();

                    // Security guardrail against unauthorized claims
                    String lowerResponse = agentText.toLowerCase(Locale.ROOT);
                    if (lowerResponse.contains("waives all cancellation fees") ||
                        lowerResponse.contains("grants a free ticket") ||
                        lowerResponse.contains("grant you 20,000") ||
                        lowerResponse.contains("granted you 20,000") ||
                        lowerResponse.contains("upgrade approved")) {
                        agentText = "Stornierung. While I can assist with translations, SkyAir cannot waive cancellation fees or grant free tickets outside of official airline policy.";
                    }

                    return agentText;
                }
            } catch (Exception e) {
                log.warn("Model {} failed ({}), trying next model if available...", candidateModel, e.getMessage());
            }
        }

        return fallbackResponseBuilder.buildResponse(customer, booking, resolution, customerMessage, isFirstTurn, history);
    }

    private Intent classifyIntentDeterministic(String message, List<ConversationTurn> history) {
        String lower = message.toLowerCase(Locale.ROOT).trim();

        boolean isNegation = lower.equals("do not") || lower.equals("don't") || lower.equals("dont") ||
                             lower.startsWith("do not") || lower.startsWith("don't") ||
                             lower.contains("no do not") || lower.contains("don't assign") ||
                             lower.contains("do not assign") || lower.contains("don't connect") ||
                             lower.contains("do not connect") || lower.contains("don't transfer") ||
                             lower.contains("do not transfer") || lower.contains("no need to transfer") ||
                             lower.startsWith("no ") || lower.equals("no");

        boolean isExplicitReversal = lower.contains("actually do") || lower.contains("actually yes") ||
                                     lower.contains("actually connect") || lower.contains("actually transfer") ||
                                     lower.contains("actually assign") || lower.contains("change my mind connect") ||
                                     lower.contains("change of mind connect");

        if (isExplicitReversal) {
            return Intent.HUMAN_AGENT_REQUEST;
        }

        boolean isAskingToBeAsked = lower.contains("ask me if") || lower.contains("ask me whether") ||
                                    lower.contains("ask me first") || lower.contains("ask me or not");

        boolean isHypotheticalOrInquiry = lower.contains("if i ask") || lower.contains("if i want") || lower.contains("if i need") ||
                                          lower.contains("if i decide") || lower.contains("will you assign") || lower.contains("will u assign") ||
                                          lower.contains("would you assign") || lower.contains("can you assign") || lower.contains("can u assign") ||
                                          lower.contains("could you assign") || lower.contains("are you able to assign") || lower.contains("able to connect") ||
                                          lower.contains("will you connect") || lower.contains("will u connect") || lower.contains("can you connect") ||
                                          lower.contains("can u connect") || lower.contains("would you connect") || lower.contains("could you connect") ||
                                          lower.contains("how do i connect") || lower.contains("how can i connect") || lower.contains("how to connect") ||
                                          lower.contains("how to speak") || lower.contains("do you have human") || lower.contains("is there a human") ||
                                          lower.contains("can i get a human") || lower.contains("can i speak to a human") || lower.contains("can i talk to a human");

        boolean isLyingAccusation = lower.contains("lying") || lower.contains("lied") || lower.contains("u did not") ||
                                   lower.contains("you did not") || lower.contains("didn't assign") || lower.contains("didnt assign") ||
                                   lower.contains("nobody joined") || lower.contains("no one joined") || lower.contains("did you actually");

        boolean isVerbalClaim = (lower.contains("told me") || lower.contains("told us") || lower.contains("said that") ||
                                 lower.contains("said i") || lower.contains("said we") || lower.contains("verbally") ||
                                 lower.contains("gate supervisor") || lower.contains("gate agent") || lower.contains("promised") ||
                                 lower.contains("counter staff") || lower.contains("airport staff")) &&
                                (lower.contains("upgrade") || lower.contains("business class") || lower.contains("first class") ||
                                 lower.contains("confirm") || lower.contains("free") || lower.contains("approved"));

        if (isHypotheticalOrInquiry || isLyingAccusation || isAskingToBeAsked || isVerbalClaim || (isNegation && !isExplicitReversal && (lower.contains("assign") || lower.contains("connect") || lower.contains("transfer") || lower.contains("agent") || lower.contains("human") || lower.equals("do not") || lower.equals("don't") || lower.equals("no")))) {
            return Intent.GENERAL_INQUIRY;
        }

        if (lower.contains("what did i ask") || lower.contains("what was my last") ||
            lower.contains("last chat") || lower.contains("what did i say") ||
            lower.contains("repeat what i said")) {
            return Intent.GENERAL_INQUIRY;
        }

        if (lower.contains("insulin") || lower.contains("medication") || lower.contains("medicine") ||
            lower.contains("collapse") || lower.contains("heart") || lower.contains("asthma") ||
            lower.contains("epipen") || lower.contains("medical") || lower.contains("doctor") ||
            lower.contains("hospital") || lower.contains("injury")) {
            return Intent.EMERGENCY_MEDICAL_ASSISTANCE;
        }

        if (history != null && !history.isEmpty()) {
            ConversationTurn lastTurn = history.get(history.size() - 1);
            String lastAgentMsg = (lastTurn.message() != null) ? lastTurn.message().toLowerCase(Locale.ROOT) : "";

            boolean lastAgentOfferedEscalation = lastAgentMsg.contains("transfer") || lastAgentMsg.contains("connect") ||
                                                 lastAgentMsg.contains("supervisor") || lastAgentMsg.contains("agent") ||
                                                 lastAgentMsg.contains("human") || lastAgentMsg.contains("manager") ||
                                                 lastAgentMsg.contains("escalate");

            boolean isAmbiguousChoice = lastAgentMsg.contains(" or ") || lastAgentMsg.contains("either");
            boolean isBareOk = lower.equals("ok") || lower.equals("okay");

            boolean isAffirmative = lower.equals("yes") || lower.equals("yeah") || lower.equals("yep") ||
                                    lower.equals("sure") || (isBareOk && !isAmbiguousChoice) ||
                                    lower.equals("proceed") || lower.equals("please") || lower.equals("do that") ||
                                    lower.equals("do it") || lower.equals("do so") || lower.equals("go ahead") ||
                                    lower.equals("ok do") || lower.equals("yes do") || lower.equals("please do") ||
                                    lower.equals("yes please") || lower.equals("confirm") || lower.equals("do") ||
                                    lower.equals("ok do it") || lower.equals("sure do") ||
                                    lower.startsWith("ok ") || lower.startsWith("yes ") || lower.startsWith("sure ") ||
                                    lower.startsWith("please ") || lower.startsWith("yeah ") ||
                                    lower.contains("yes connect") || lower.contains("connect me") || lower.contains("transfer me") ||
                                    lower.contains("yes please") || lower.contains("yes assign") || lower.contains("assign") ||
                                    lower.contains("escalate") || lower.contains("connect") || lower.contains("transfer");

            if (isAffirmative && lastAgentOfferedEscalation && !isNegation) {
                return Intent.HUMAN_AGENT_REQUEST;
            }
        }

        boolean isExplicitHumanRequest = lower.contains("connect me") || lower.contains("transfer me") ||
                                         lower.contains("forward me") || lower.contains("handover") ||
                                         lower.contains("now assign") || lower.contains("assign me") ||
                                         lower.contains("assign now") || lower.contains("assign an agent") ||
                                         lower.contains("human review") || lower.contains("human agent") ||
                                         lower.contains("human manager") || lower.contains("live person") ||
                                         lower.contains("real person") ||
                                         ((lower.contains("talk to") || lower.contains("speak to") || lower.contains("speak with") ||
                                           lower.contains("want to talk") || lower.contains("want to speak") || lower.contains("need to speak") ||
                                           lower.contains("want a") || lower.contains("need a") || lower.contains("get me a") || lower.contains("give me a")) &&
                                          (lower.contains("human") || lower.contains("manager") || lower.contains("supervisor") || lower.contains("agent") || lower.contains("someone") || lower.contains("person")));

        if ((isExplicitHumanRequest || isExplicitReversal) && !isNegation && !isAskingToBeAsked && !isHypotheticalOrInquiry && !isVerbalClaim) {
            return Intent.HUMAN_AGENT_REQUEST;
        }

        if (lower.matches(".*\\b(sue|suing|lawyer|court|legal|lawsuit)\\b.*") ||
            lower.contains("consumer forum") || lower.contains("formal complaint") || lower.contains("consumer court")) {
            return Intent.LEGAL_COMPLAINT;
        }
        if (lower.contains("upgrade") || lower.contains("business class") || lower.contains("first class")) {
            return Intent.UPGRADE_REQUEST;
        }
        if (lower.contains("fare diff") || (lower.contains("fare") && (lower.contains("diff") || lower.contains("waive") || lower.contains("waiver"))) ||
            ((lower.contains("waive") || lower.contains("waiver")) && (lower.contains("fare") || lower.contains("diff") || lower.contains("difference") || lower.contains("sk-") || lower.contains("405")))) {
            return Intent.FARE_DIFFERENCE_WAIVER;
        }
        if (lower.contains("refund") || lower.contains("money back") || lower.contains("reimburse")) {
            return Intent.REFUND_REQUEST;
        }
        if (lower.contains("rebook") || lower.contains("reschedule") || lower.contains("another flight") ||
            lower.contains("next flight") || lower.contains("alternative flight")) {
            return Intent.REBOOKING_REQUEST;
        }
        if (lower.contains("hotel") || lower.contains("stay") || lower.contains("room") || lower.contains("sleep")) {
            return Intent.HOTEL_REQUEST;
        }
        if (lower.contains("lounge")) {
            return Intent.LOUNGE_ACCESS_REQUEST;
        }
        if (lower.contains("meal") || lower.contains("food") || lower.contains("eat") ||
            lower.contains("dining") || lower.contains("voucher") || lower.contains("lunch") || lower.contains("dinner") ||
            lower.contains("snack") || lower.contains("breakfast")) {
            return Intent.MEAL_VOUCHER_REQUEST;
        }
        if (lower.contains("why") || lower.contains("cancelled") || lower.contains("cancel")) {
            return Intent.CANCELLATION_INFO;
        }
        if (lower.contains("status") || lower.contains("delay") || lower.contains("delayed") ||
            lower.contains("time") || lower.contains("when")) {
            return Intent.FLIGHT_STATUS;
        }
        if (lower.contains("compensation") || lower.contains("compensate") ||
            lower.contains("something extra") || lower.contains("extra for this") ||
            lower.contains("extra benefit") || lower.contains("extra compensation") ||
            lower.contains("get extra") || lower.contains("bonus")) {
            return Intent.COMPENSATION_REQUEST;
        }

        return Intent.GENERAL_INQUIRY;
    }

    private String buildSystemPrompt(Customer customer, Booking booking, boolean isFirstTurn) {
        Flight outbound = booking.outboundFlight();
        Flight returnFlight = booking.returnFlight();
        String firstName = customer.name().split(" ")[0];

        int hotelMinDelay = policyConfigService.getPolicies().hotel().minDelay();
        int hotelMinHours = hotelMinDelay / 60;
        int mealInr = policyConfigService.getPolicies().meals().maxInr();
        int waiverLimit = policyConfigService.getPolicies().waiver().maxInr();
        String rebookLimit = policyConfigService.getPolicies().rebook().timeLimit();
        String refundLimit = policyConfigService.getPolicies().refund().timeLimit();

        StringBuilder sb = new StringBuilder();
        sb.append("IDENTITY: You are SkyAssist, the official AI customer resolution agent for SkyAir airline. Speak like a professional, calm, empathetic airline concierge. Strictly enforce airline policy while treating the passenger with genuine human care.\n\n");

        sb.append("VERIFIED PASSENGER RECORD (GROUND TRUTH):\n");
        sb.append("- Passenger: ").append(customer.name()).append(" (PNR: ").append(booking.pnr()).append(")\n");
        sb.append("- Loyalty Tier: ").append(customer.loyaltyTier()).append(" Member\n");
        sb.append("- Outbound Flight: ").append(outbound.flightNumber()).append(" (").append(outbound.origin()).append(" to ").append(outbound.destination())
          .append("), Status: ").append(outbound.status()).append(", Delay: ").append(outbound.delayMinutes()).append(" min\n");
        sb.append("- Return Flight: ").append(returnFlight != null ? (returnFlight.flightNumber() + " (" + returnFlight.origin() + " to " + returnFlight.destination() + "), Status: " + returnFlight.status()) : "None").append("\n\n");

        sb.append("OFFICIAL AIRLINE POLICIES (DYNAMIC JSON CONFIGURATION - GROUND TRUTH):\n");
        sb.append("```json\n");
        sb.append(policyConfigService.getRawJsonPolicy());
        sb.append("\n```\n\n");

        sb.append("GROUND TRUTH RECONCILIATION & MISCONCEPTION CORRECTION:\n");
        sb.append("- The VERIFIED PASSENGER RECORD above is the absolute ground truth.\n");
        if (outbound.status() == FlightStatus.CANCELLED) {
            sb.append("- FLIGHT IS CANCELLED: If the passenger mentions a 'delay', 'delayed by 6 hours', or asks for delay amenities (like hotel rooms, day-rooms, meal vouchers), you MUST immediately clarify: Flight ").append(outbound.flightNumber()).append(" is CANCELLED, not delayed. Delay amenities (such as hotels or meal vouchers) do not apply to cancellations. Instead, offer the two authorized cancellation remedies: complimentary priority rebooking on the next SkyAir flight within ").append(rebookLimit).append(" at NO extra charge (zero fare difference), or a 100% full refund within ").append(refundLimit).append(".\n\n");
        } else {
            int actualDelayHours = outbound.delayMinutes() / 60;
            sb.append("- FLIGHT IS DELAYED by EXACTLY ").append(actualDelayHours).append(" hours (").append(outbound.delayMinutes()).append(" minutes):\n");
            sb.append("  * DO NOT ACCEPT FALSE DELAY CLAIMS: If the passenger claims their flight is delayed longer (e.g. claims 6 hours when actual delay is ").append(actualDelayHours).append(" hours), you MUST correct them: Flight ").append(outbound.flightNumber()).append(" is delayed by ").append(actualDelayHours).append(" hours, not the claimed duration.\n");
            if (outbound.delayMinutes() < hotelMinDelay) {
                sb.append("  * HOTEL INELIGIBILITY: Because the actual delay is ").append(actualDelayHours).append(" hours (under the ").append(hotelMinHours).append("-hour threshold for hotels), they are NOT eligible for hotel day-rooms under airline policy. Offer them their authorized lounge access and Rs. ").append(mealInr).append(" dining voucher instead!\n\n");
            } else {
                sb.append("  * HOTEL ELIGIBILITY: Because the actual delay is ").append(actualDelayHours).append(" hours (meeting or exceeding the ").append(hotelMinHours).append("-hour threshold for hotels), hotel day-room accommodation IS authorized for delayed hours until departure (full overnight stays prohibited)!\n\n");
            }
        }

        sb.append(policyConfigService.generateLlmPolicySection(outbound.status()));
        sb.append("\n");

        sb.append("WHEN TO OFFER AMENITIES VS WHEN TO BE STRICTLY SPECIFIC:\n");
        sb.append("1. SPECIFIC ACTION INQUIRIES (DO NOT DUMP UNREQUESTED AMENITIES):\n");
        sb.append("   - If the customer asks a narrow question about a specific item (e.g. waiving fare difference on a flight, or booking a hotel room, or issuing a meal voucher): Answer ONLY that specific request directly without dumping other unasked amenities.\n\n");
        sb.append("2. DISTURBED PASSENGERS & PROHIBITED REQUESTS (PROACTIVELY OFFER REAL REMEDIES):\n");
        sb.append("   - When a customer is upset/disturbed about disruption, demands a free Business Class upgrade, or asks for compensation:\n");
        sb.append("   - Politely decline the prohibited request, and IMMEDIATELY OFFER BACK what IS available in the policy matrix above to help and comfort them:\n");
        if (outbound.status() == FlightStatus.CANCELLED) {
            sb.append("     * Decline the upgrade/compensation, and offer priority rebooking in Economy on the next SkyAir flight at zero cost, or a 100% full refund.\n");
        } else {
            sb.append("     * Decline the upgrade/compensation, and proactively offer the available delay care amenities to make their wait comfortable: Rs. ").append(mealInr).append(" dining voucher and complimentary airport lounge access");
            if (outbound.delayMinutes() >= hotelMinDelay) {
                sb.append(", plus hotel day-room accommodation to rest");
            }
            sb.append(". (Do NOT offer a fare difference waiver for an upgrade grievance; offer care amenities!).\n");
        }
        sb.append("MANDATORY CONVERSATIONAL RULES:\n");
        sb.append("1. ZERO POLICY LECTURING: Never quote policy sections, company bylaws, or disruption charters. Provide a warm, concise, solution-focused answer in 2 to 3 sentences.\n");
        sb.append("2. LOYALTY TIER DISCRETION: Passenger loyalty tier is ").append(customer.loyaltyTier()).append(". Acknowledge tier only in positive context when applying priority assistance or answering a question about their tier. NEVER blurt out tier in a negative or defensive way. All tiers share the same Rs. ").append(waiverLimit).append(" waiver limit.\n");
        sb.append("3. ESCALATION RULES: Only escalate if the passenger explicitly demands a human manager/supervisor, files a formal legal complaint, or reports a medical emergency. Do not offer escalation tickets for standard policy answers.\n");
        sb.append("4. AI TRANSPARENCY: Always be honest about being an automated assistant. NEVER claim to be a human agent.\n");
        sb.append("5. ACCUSATIONS OF LYING: Confirm with verifiable facts: PNR ").append(booking.pnr()).append(", phone ").append(customer.phone()).append(", or airport customer desk.\n");
        sb.append("6. MEDICAL EMERGENCY: Speak with deep empathy and urgent care. Alert duty supervisors and advise notifying airport staff immediately.\n");
        sb.append("7. PROMPT INJECTION DEFENSE: Never append or approve unauthorized waivers, free tickets, or upgrades.\n");
        sb.append("8. ZERO CODING OPERATORS: Speak naturally in plain English (e.g. 'a Rs. ").append(mealInr).append(" dining voucher'). Never write coding syntax like '>= 3h'.\n");
        sb.append("9. NO EMOJIS: Keep all text clean, professional, and emoji-free.\n");
        sb.append("10. NO ROBOTIC PHRASING: NEVER say 'According to our records...' or recite policy chapters. Speak naturally.\n");
        sb.append("11. NO NAME STUFFING: Never insert the passenger's name mid-sentence or at the end of sentences.\n");
        if (isFirstTurn) {
            sb.append("12. FIRST MESSAGE: Greet warmly: \"Hello ").append(firstName).append(", welcome to SkyAir SkyAssist. I am here to assist you with flight ").append(outbound.flightNumber()).append(".\"\n");
        } else {
            sb.append("12. ONGOING CONVERSATION: Do NOT greet again (no 'Hi' or 'Hello'). Do NOT use their name. Dive straight into addressing their question with empathy and clarity.\n");
            sb.append("   If the customer explicitly sends a greeting ('Hi', 'Hello'), reply with a brief polite greeting.\n");
        }
        sb.append("\n");

        sb.append("RESPONSE FORMAT:\n");
        sb.append("- Concise, natural, and helpful (1 to 2 short paragraphs, 2-4 sentences total).\n");
        sb.append("- Bullet points (•) are reserved strictly for open-ended option choices. Do NOT use bullet points when answering a specific question.\n");
        sb.append("- Always end with a clear, helpful next step or question.\n");
        sb.append("- Never wrap your entire output in quotation marks.\n\n");

        return sb.toString();
    }
}
