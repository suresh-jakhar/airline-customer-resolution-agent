let currentCustomerId = 'priya_nair';
let currentSessionId = '';
let isProcessing = false;

const ACTION_CONFIG = {
    FULL_REFUND: {
        label: 'Initiate Full Refund',
        description: 'Original payment method · 7 business days'
    },
    REBOOK: {
        label: 'Rebook on Next Flight',
        description: 'Complimentary within 24h · Priority tier access'
    },
    MEAL_VOUCHER: {
        label: 'Apply ₹500 Meal Voucher',
        description: 'Valid at all airport dining venues'
    },
    LOUNGE_ACCESS: {
        label: 'Grant Airport Lounge Pass',
        description: 'Complimentary lounge admission'
    },
    HOTEL_DELAYED_HOURS: {
        label: 'Arrange Hotel (Delayed Hours)',
        description: 'Accommodation during delay period (not full night)'
    }
};

const CUSTOMER_CHIPS = {
    priya_nair: [
        { label: 'Why was flight SK-204 cancelled?', msg: 'Why was flight SK-204 cancelled?' },
        { label: 'Request 100% full refund', msg: 'I want to request a full refund to my original payment method.' },
        { label: 'Rebook on next flight within 24h', msg: 'Please rebook me on the next available flight within 24 hours.' },
        { label: 'Check return flight SK-205 status', msg: 'What is the status of my return flight SK-205?' },
        { label: 'I am furious! Upgrade my return flight', msg: 'I am furious about this cancellation! I want a full refund plus a free upgrade to business class on my return flight.' },
        { label: 'Connect me with a supervisor', msg: 'I want to speak with a human customer care supervisor.' }
    ],
    arvind_kulkarni: [
        { label: 'What is the delay status of SK-118?', msg: 'What is the updated status of flight SK-118?' },
        { label: 'Can you arrange hotel for my 4h delay?', msg: 'I am really frustrated about missing my connecting meeting. Can you arrange hotel accommodation for me since it has been such a long delay?' },
        { label: 'Claim ₹600 dining voucher', msg: 'Can I get a ₹600 meal voucher for this 4 hour delay?' },
        { label: 'Request airport lounge access pass', msg: 'Am I eligible for airport lounge access during my wait?' },
        { label: 'I am missing a critical meeting in BLR', msg: 'I am missing a critical meeting in Bengaluru due to this 4-hour delay. What remedies can you provide?' },
        { label: 'Transfer to a human representative', msg: 'Please transfer me to a human customer care representative.' }
    ],
    meher_kaur: [
        { label: 'Why is flight SK-305 delayed 6 hours?', msg: 'What is the reason for the 6 hour delay on flight SK-305?' },
        { label: 'Book a full night hotel room for me', msg: 'I want a full night hotel stay rather than coverage for just the delayed hours.' },
        { label: 'Move to earlier flight & waive ₹2,000 fare diff', msg: 'Can you move me onto an alternate higher-fare flight without waiting? Can you waive the Rs. 2,000 fare difference?' },
        { label: 'Claim meal voucher & lounge access', msg: 'What care amenities can you provide for my 6 hour delay?' },
        { label: 'What are my Platinum member remedies?', msg: 'As a Platinum member, what special remedies or priority rebooking do I receive?' },
        { label: 'Transfer to duty manager immediately', msg: 'I want to speak with a supervisor immediately.' }
    ]
};

const CITY_NAMES = {
    DEL: 'Delhi',
    GOA: 'Goa',
    BOM: 'Mumbai',
    BLR: 'Bangalore',
    HYD: 'Hyderabad'
};

const SPARKLE_AVATAR_SVG = `
<svg width="16" height="16" viewBox="0 0 24 24" fill="currentColor">
    <path d="M12 2L14.4 9.6L22 12L14.4 14.4L12 22L9.6 14.4L2 12L9.6 9.6L12 2Z"/>
    <circle cx="19" cy="5" r="1.5"/>
</svg>
`;

document.addEventListener('DOMContentLoaded', () => {
    initUI();
    fetchPolicies();
    loadCustomer(currentCustomerId);
    checkHealth();
});

async function fetchPolicies() {
    try {
        const res = await fetch('/api/policies');
        if (res.ok) {
            const data = await res.json();
            if (data && data.actions) {
                Object.keys(data.actions).forEach(key => {
                    ACTION_CONFIG[key] = {
                        label: data.actions[key].label || ACTION_CONFIG[key]?.label,
                        description: data.actions[key].description || ACTION_CONFIG[key]?.description
                    };
                });
            }
            if (data && data.policies && data.policies.meals) {
                const mealInr = data.policies.meals.maxInr || data.policies.meals.max_inr || 600;
                const arvindChip = CUSTOMER_CHIPS.arvind_kulkarni?.find(c => c.label.includes('dining voucher'));
                if (arvindChip) {
                    arvindChip.label = `Claim ₹${mealInr} dining voucher`;
                    arvindChip.msg = `Can I get a ₹${mealInr} meal voucher for this 4 hour delay?`;
                }
                if (currentCustomerId === 'arvind_kulkarni') {
                    renderQuickChips('arvind_kulkarni');
                }
            }
        }
    } catch (e) {
        console.warn('Could not load dynamic policies:', e);
    }
}

function initUI() {
    const selector = document.getElementById('customerSelector');
    if (selector) {
        selector.addEventListener('change', (e) => {
            currentCustomerId = e.target.value;
            currentSessionId = '';
            loadCustomer(currentCustomerId);
        });
    }

    const chatForm = document.getElementById('chatForm');
    if (chatForm) {
        chatForm.addEventListener('submit', (e) => {
            e.preventDefault();
            const input = document.getElementById('chatInput');
            const text = input.value.trim();
            if (text && !isProcessing) {
                input.value = '';
                sendMessage(text);
            }
        });
    }

    const quickChips = document.getElementById('quickChips');
    if (quickChips) {
        quickChips.addEventListener('click', (e) => {
            const chip = e.target.closest('.chip');
            if (chip && !isProcessing) {
                const msg = chip.getAttribute('data-msg');
                if (msg) {
                    sendMessage(msg);
                }
            }
        });
    }

    const clearBtn = document.getElementById('clearChatBtn');
    if (clearBtn) {
        clearBtn.addEventListener('click', () => {
            currentSessionId = '';
            loadCustomer(currentCustomerId);
        });
    }
}

function renderQuickChips(customerId) {
    const chipsContainer = document.getElementById('quickChips');
    if (!chipsContainer) return;
    const chips = CUSTOMER_CHIPS[customerId] || CUSTOMER_CHIPS.priya_nair;
    chipsContainer.innerHTML = chips
        .map(c => `<button class="chip" data-msg="${escapeHtml(c.msg)}">${escapeHtml(c.label)}</button>`)
        .join('');
}

function escapeHtml(text) {
    if (!text) return '';
    return text.replace(/&/g, '&amp;').replace(/"/g, '&quot;').replace(/'/g, '&#39;').replace(/</g, '&lt;').replace(/>/g, '&gt;');
}

function formatTime(timeStr) {
    if (!timeStr) return '—';
    if (typeof timeStr === 'string' && timeStr.length >= 5) {
        return timeStr.substring(0, 5);
    }
    return timeStr;
}

async function checkHealth() {
    try {
        const res = await fetch('/api/health');
        if (res.ok) {
            const data = await res.json();
            const badgeText = document.getElementById('llmStatusText');
            if (badgeText) {
                badgeText.textContent = data.llmConfigured ? 'LLM Connected' : 'Deterministic Engine';
            }
        }
    } catch (e) {
        console.warn('Health check error:', e);
    }
}

async function loadCustomer(customerId) {
    try {
        const url = `/api/session/${encodeURIComponent(customerId)}${currentSessionId ? `?sessionId=${encodeURIComponent(currentSessionId)}` : ''}`;
        const res = await fetch(url);
        if (!res.ok) throw new Error('Failed to load customer profile');

        const session = await res.json();
        currentSessionId = session.sessionId;

        renderCustomerProfile(session.customer, session.booking);
        renderQuickChips(customerId);
        resetChatStream(session.customer, session.booking, session.conversationHistory);
        resetTelemetry(session.sessionId);

        // Hide escalation banner and action dock on fresh switch
        const escBanner = document.getElementById('escalationBanner');
        if (escBanner) escBanner.style.display = 'none';
        clearActionButtons();

    } catch (err) {
        console.error('Error loading customer:', err);
        alert('Could not load customer context. Please verify the backend is running.');
    }
}

async function syncCustomerContext(customerId) {
    try {
        currentCustomerId = customerId;
        const selector = document.getElementById('customerSelector');
        if (selector) selector.value = customerId;
        const res = await fetch(`/api/session/${encodeURIComponent(customerId)}`);
        if (res.ok) {
            const session = await res.json();
            renderCustomerProfile(session.customer, session.booking);
            renderQuickChips(customerId);
        }
    } catch (e) {
        console.warn('Could not sync customer context:', e);
    }
}

function renderCustomerProfile(customer, booking) {
    const custName = document.getElementById('customerName');
    if (custName) custName.textContent = customer.name;

    const custAvatar = document.getElementById('customerAvatar');
    if (custAvatar) {
        const initials = customer.name.split(' ').map(n => n[0]).join('');
        custAvatar.textContent = initials;
    }

    // Tier badge
    const tierBadge = document.getElementById('tierBadge');
    const tierText = document.getElementById('tierText');
    if (tierBadge && tierText) {
        tierText.textContent = `${customer.loyaltyTier} MEMBER`;
    }

    const pnrText = document.getElementById('pnrText');
    if (pnrText) pnrText.textContent = booking.pnr;

    const emailText = document.getElementById('emailText');
    if (emailText) emailText.textContent = customer.email;

    const phoneText = document.getElementById('phoneText');
    if (phoneText) phoneText.textContent = customer.phone;

    const flightCountText = document.getElementById('flightCountText');
    if (flightCountText) flightCountText.textContent = `${customer.flightCount} completed flights`;

    const complaintRow = document.getElementById('complaintRow');
    const complaintText = document.getElementById('complaintText');
    if (complaintRow && complaintText) {
        if (customer.priorComplaintSummary) {
            complaintRow.style.display = 'flex';
            complaintText.textContent = customer.priorComplaintSummary;
        } else {
            complaintRow.style.display = 'none';
        }
    }

    // Flight Details
    const flight = booking.outboundFlight;
    const flightNumber = document.getElementById('flightNumber');
    if (flightNumber) flightNumber.textContent = flight.flightNumber;

    const statusBadge = document.getElementById('flightStatusBadge');
    if (statusBadge) {
        statusBadge.className = `status-mono-badge ${flight.status.toLowerCase()}`;
        statusBadge.textContent = flight.status;
    }

    const originCode = document.getElementById('originCode');
    if (originCode) originCode.textContent = flight.origin;

    const originCity = document.getElementById('originCity');
    if (originCity) originCity.textContent = CITY_NAMES[flight.origin] || flight.origin;

    const destCode = document.getElementById('destCode');
    if (destCode) destCode.textContent = flight.destination;

    const destCity = document.getElementById('destCity');
    if (destCity) destCity.textContent = CITY_NAMES[flight.destination] || flight.destination;

    const scheduledDep = document.getElementById('scheduledDep');
    if (scheduledDep) scheduledDep.textContent = formatTime(flight.scheduledDeparture);

    const flightDuration = document.getElementById('flightDuration');
    const newDep = document.getElementById('newDep');

    if (flight.status === 'CANCELLED') {
        if (flightDuration) flightDuration.textContent = 'Operational Cancellation';
        if (newDep) newDep.textContent = 'CANCELLED';
    } else if (flight.status === 'DELAYED') {
        const hours = Math.round(flight.delayMinutes / 60);
        if (flightDuration) flightDuration.textContent = `Delayed by ${hours} hours`;
        if (newDep) newDep.textContent = formatTime(flight.newDeparture);
    }

    // Return Flight Box (Full Ticket)
    const returnBox = document.getElementById('returnFlightBox');
    if (returnBox) {
        if (booking.returnFlight) {
            returnBox.style.display = 'flex';
            const rf = booking.returnFlight;
            const retFlightNum = document.getElementById('returnFlightNumber');
            if (retFlightNum) retFlightNum.textContent = rf.flightNumber;
            
            const retStatusBadge = document.getElementById('returnFlightStatus');
            if (retStatusBadge) retStatusBadge.textContent = 'CONFIRMED';
            
            const retOrigCode = document.getElementById('returnOriginCode');
            if (retOrigCode) retOrigCode.textContent = rf.origin;
            
            const retOrigCity = document.getElementById('returnOriginCity');
            if (retOrigCity) retOrigCity.textContent = CITY_NAMES[rf.origin] || rf.origin;
            
            const retDestCode = document.getElementById('returnDestCode');
            if (retDestCode) retDestCode.textContent = rf.destination;
            
            const retDestCity = document.getElementById('returnDestCity');
            if (retDestCity) retDestCity.textContent = CITY_NAMES[rf.destination] || rf.destination;
            
            const retDep = document.getElementById('returnScheduledDep');
            if (retDep) retDep.textContent = formatTime(rf.scheduledDeparture) + ' (25 Sep)';
            
            const retReason = document.getElementById('returnFlightDuration');
            if (retReason) retReason.textContent = 'Confirmed On Schedule';
            
            const retStatusVal = document.getElementById('returnStatusVal');
            if (retStatusVal) retStatusVal.textContent = 'ON TIME';
        } else {
            returnBox.style.display = 'none';
        }
    }

    // Reset resolution state
    const liveRes = document.getElementById('liveResolutionState');
    if (liveRes) liveRes.textContent = 'Awaiting Decision';
}

function resetChatStream(customer, booking, history) {
    const stream = document.getElementById('chatStream');
    if (!stream) return;
    stream.innerHTML = '';

    // Filter out any legacy automatic canned greetings if present
    const validHistory = (history || []).filter(turn => {
        if (!turn || !turn.message) return false;
        const msg = turn.message.trim();
        if (turn.role === 'agent' && (msg.includes('welcome to SkyAir SkyAssist') && (msg.includes('How would you like to proceed today?') || msg.includes('How may I assist you right now?')))) {
            return false;
        }
        return true;
    });

    if (validHistory.length > 0) {
        validHistory.forEach(turn => {
            appendMessage(turn.role, turn.message, new Date(turn.timestamp).toLocaleTimeString([], { hour: '2-digit', minute: '2-digit' }));
        });
        return;
    }

    // Render clean empty state matching user screenshot
    const emptyDiv = document.createElement('div');
    emptyDiv.className = 'chat-empty-state';
    emptyDiv.id = 'chatEmptyState';
    emptyDiv.innerHTML = `
        <div class="empty-sparkle-icon">
            <svg width="28" height="28" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.8" stroke-linecap="round" stroke-linejoin="round">
                <path d="M12 2L14.4 9.6L22 12L14.4 14.4L12 22L9.6 14.4L2 12L9.6 9.6L12 2Z"/>
                <path d="M19 3L19.8 5.4L22 6L19.8 6.6L19 9L18.2 6.6L16 6L18.2 5.4L19 3Z"/>
            </svg>
        </div>
        <h3 class="empty-title">How can I help you today?</h3>
        <p class="empty-subtitle">Ask me anything and I'll do my best to assist you!</p>
    `;
    stream.appendChild(emptyDiv);

    logActivity('info', `Session ready for passenger ${customer.name} (PNR: ${booking.pnr})`);
}

function resetTelemetry(sessionId) {
    const sessEl = document.getElementById('telemetrySessionId');
    if (sessEl) sessEl.textContent = sessionId;
    const intentEl = document.getElementById('telemetryIntent');
    if (intentEl) intentEl.textContent = 'AWAITING_INPUT';
}

async function sendMessage(text) {
    if (isProcessing) return;
    isProcessing = true;
    toggleInputState(true);

    appendMessage('customer', text);
    logActivity('intent', `Customer: "${text}"`);

    const typingIndicator = showTypingIndicator();

    try {
        const payload = {
            customerId: currentCustomerId,
            message: text,
            sessionId: currentSessionId
        };

        const res = await fetch('/api/chat', {
            method: 'POST',
            headers: { 'Content-Type': 'application/json' },
            body: JSON.stringify(payload)
        });

        removeTypingIndicator(typingIndicator);

        if (!res.ok) {
            throw new Error(`Server returned HTTP ${res.status}`);
        }

        const data = await res.json();
        currentSessionId = data.sessionId;
        const sessEl = document.getElementById('telemetrySessionId');
        if (sessEl) sessEl.textContent = currentSessionId;

        if (data.customerId && data.customerId !== currentCustomerId) {
            syncCustomerContext(data.customerId);
        }

        appendMessage('agent', data.agentMessage);

        if (data.detectedIntent) {
            const intentEl = document.getElementById('telemetryIntent');
            if (intentEl) intentEl.textContent = data.detectedIntent;
            logActivity('intent', `Intent: ${data.detectedIntent}`);
        }

        if (data.resolution) {
            updateTelemetry(data.resolution);
            handleEscalation(data.resolution);

            if (data.resolution.allowedActions && data.resolution.allowedActions.length > 0) {
                renderActionButtons(data.resolution.allowedActions);
            } else if (data.resolution.escalationRequired) {
                renderEscalationActionIndicator(data.resolution.escalationReason);
            } else {
                clearActionButtons();
            }
        }

    } catch (err) {
        removeTypingIndicator(typingIndicator);
        console.error('Chat error:', err);
        appendMessage('agent', 'I encountered a communication error connecting with the resolution services. Please try sending your message again.');
        logActivity('escalate', `Network / API error: ${err.message}`);
    } finally {
        isProcessing = false;
        toggleInputState(false);
    }
}

async function executeAction(actionType) {
    if (isProcessing) return;
    isProcessing = true;
    toggleInputState(true);

    logActivity('action', `Executing resolution action: ${actionType}`);

    try {
        const payload = {
            sessionId: currentSessionId,
            customerId: currentCustomerId,
            actionType: actionType
        };

        const res = await fetch('/api/actions/execute', {
            method: 'POST',
            headers: { 'Content-Type': 'application/json' },
            body: JSON.stringify(payload)
        });

        if (!res.ok) {
            const errData = await res.json().catch(() => ({}));
            throw new Error(errData.message || `HTTP ${res.status}`);
        }

        const data = await res.json();
        
        appendMessage('agent', data.confirmationMessage);
        logActivity('policy', `Action completed: ${actionType}`);

        const liveRes = document.getElementById('liveResolutionState');
        if (liveRes) {
            liveRes.textContent = `${ACTION_CONFIG[actionType]?.label || actionType} Executed`;
        }

        if (data.remainingAllowedActions && data.remainingAllowedActions.length > 0) {
            renderActionButtons(data.remainingAllowedActions);
        } else {
            const group = document.getElementById('actionButtonsGroup');
            const label = document.getElementById('actionDockLabel');
            if (label) {
                label.style.display = 'block';
                label.textContent = 'Resolution Status:';
            }
            if (group) {
                group.innerHTML = `
                    <div class="action-confirmed-pill">
                        <span>${escapeHtml(ACTION_CONFIG[actionType]?.label || actionType)} Executed</span>
                        <span style="color: var(--text-muted); font-size: 0.72rem;"> · Disruption resolution complete for this booking</span>
                    </div>
                `;
            }
        }

    } catch (err) {
        console.error('Action execution error:', err);
        appendMessage('agent', `Unable to execute action: ${err.message}`);
        logActivity('escalate', `Execution failed: ${err.message}`);
    } finally {
        isProcessing = false;
        toggleInputState(false);
    }
}

function renderActionButtons(allowedActions) {
    const group = document.getElementById('actionButtonsGroup');
    const label = document.getElementById('actionDockLabel');
    const quickChipsContainer = document.querySelector('.quick-chips-container');
    if (!group) return;

    group.innerHTML = '';

    if (!allowedActions || allowedActions.length === 0) {
        if (label) label.style.display = 'none';
        if (quickChipsContainer) quickChipsContainer.style.display = 'block';
        return;
    }

    if (quickChipsContainer) {
        quickChipsContainer.style.display = 'none';
    }

    if (label) {
        label.style.display = 'block';
        label.textContent = 'Authorized Remedies Available:';
    }

    allowedActions.forEach(action => {
        const cfg = ACTION_CONFIG[action] || { label: action };
        const btn = document.createElement('button');
        btn.className = 'btn-action';
        btn.setAttribute('data-action', action);
        btn.innerHTML = `<span>${escapeHtml(cfg.label)}</span>`;
        btn.title = cfg.description || '';
        btn.addEventListener('click', () => executeAction(action));
        group.appendChild(btn);
    });
}

function renderEscalationActionIndicator(reason) {
    const group = document.getElementById('actionButtonsGroup');
    const label = document.getElementById('actionDockLabel');
    const quickChipsContainer = document.querySelector('.quick-chips-container');
    if (quickChipsContainer) quickChipsContainer.style.display = 'none';
    if (!group) return;

    if (label) {
        label.style.display = 'block';
        label.textContent = 'Resolution Status:';
    }

    const humanReason = formatEscalationReason(reason);
    group.innerHTML = `
        <div class="escalation-action-pill">
            <span>Specialist Review Active · Request escalated (${escapeHtml(humanReason)})</span>
        </div>
    `;

    const liveRes = document.getElementById('liveResolutionState');
    if (liveRes) {
        liveRes.textContent = 'Transferred to Supervisor';
    }
}

function clearActionButtons() {
    const group = document.getElementById('actionButtonsGroup');
    if (group) group.innerHTML = '';
    const label = document.getElementById('actionDockLabel');
    if (label) label.style.display = 'none';
    const quickChipsContainer = document.querySelector('.quick-chips-container');
    if (quickChipsContainer) quickChipsContainer.style.display = 'block';
}

function formatEscalationReason(reason) {
    if (!reason) return 'Priority Supervisor Assistance';
    const REASON_MAP = {
        'HUMAN_HANDOFF_REQUESTED': 'Customer Specialist Requested',
        'CABIN_UPGRADE_REQUESTED': 'Complimentary Cabin Upgrade Review',
        'HOTEL_FULL_STAY_REQUEST': 'Extended Accommodation Review',
        'MEDICAL_PRIORITY': 'Medical Priority Assistance',
        'SUPERVISOR_ASSISTANCE': 'Supervisor Assistance',
        'COMPLAINT_SUPERVISOR_REQUEST': 'Duty Manager Review',
        'HIGH_TIER_OVERRIDE': 'Tier Exception Review'
    };
    if (REASON_MAP[reason]) return REASON_MAP[reason];
    return reason.replace(/_/g, ' ').toLowerCase().replace(/\b\w/g, l => l.toUpperCase());
}

function handleEscalation(resolution) {
    const banner = document.getElementById('escalationBanner');
    const reasonText = document.getElementById('escalationReasonText');
    if (!banner) return;

    if (resolution.escalationRequired) {
        banner.style.display = 'flex';
        const formattedReason = formatEscalationReason(resolution.escalationReason);
        if (reasonText) {
            reasonText.textContent = `${formattedReason} · A senior duty supervisor is reviewing your booking to assist you directly.`;
        }

        const liveRes = document.getElementById('liveResolutionState');
        if (liveRes) {
            liveRes.textContent = 'Transferred to Supervisor';
        }
    } else {
        banner.style.display = 'none';
        const liveRes = document.getElementById('liveResolutionState');
        if (liveRes && liveRes.textContent === 'Transferred to Supervisor') {
            liveRes.textContent = 'Awaiting Decision';
        }
    }
}

function updateTelemetry(resolution) {
    const policiesList = document.getElementById('appliedPoliciesList');
    if (policiesList && resolution.policiesApplied && resolution.policiesApplied.length > 0) {
        policiesList.innerHTML = resolution.policiesApplied
            .map(p => `<div class="policy-tag">${p}</div>`)
            .join('');
    }
}

function appendMessage(role, text, timeStr) {
    const stream = document.getElementById('chatStream');
    if (!stream) return;

    const emptyState = document.getElementById('chatEmptyState');
    if (emptyState && emptyState.parentNode) {
        emptyState.parentNode.removeChild(emptyState);
    }

    const msgDiv = document.createElement('div');
    msgDiv.className = `chat-message ${role}`;

    const avatarDiv = document.createElement('div');
    avatarDiv.className = 'msg-avatar';
    if (role === 'agent') {
        avatarDiv.innerHTML = SPARKLE_AVATAR_SVG;
    } else {
        avatarDiv.textContent = 'You';
    }

    const contentDiv = document.createElement('div');
    contentDiv.className = 'msg-content';

    const bubbleDiv = document.createElement('div');
    bubbleDiv.className = 'msg-bubble';
    bubbleDiv.textContent = text;

    const metaDiv = document.createElement('div');
    metaDiv.className = 'msg-meta';
    metaDiv.textContent = timeStr || new Date().toLocaleTimeString([], { hour: '2-digit', minute: '2-digit' });

    contentDiv.appendChild(bubbleDiv);
    contentDiv.appendChild(metaDiv);

    msgDiv.appendChild(avatarDiv);
    msgDiv.appendChild(contentDiv);

    stream.appendChild(msgDiv);
    stream.scrollTop = stream.scrollHeight;
}

function showTypingIndicator() {
    const stream = document.getElementById('chatStream');
    if (!stream) return null;

    const emptyState = document.getElementById('chatEmptyState');
    if (emptyState && emptyState.parentNode) {
        emptyState.parentNode.removeChild(emptyState);
    }

    const indicator = document.createElement('div');
    indicator.className = 'chat-message agent typing-msg';
    indicator.innerHTML = `
        <div class="msg-avatar">${SPARKLE_AVATAR_SVG}</div>
        <div class="typing-indicator">
            <span class="typing-dot"></span>
            <span class="typing-dot"></span>
            <span class="typing-dot"></span>
        </div>
    `;
    stream.appendChild(indicator);
    stream.scrollTop = stream.scrollHeight;
    return indicator;
}

function removeTypingIndicator(indicator) {
    if (indicator && indicator.parentNode) {
        indicator.parentNode.removeChild(indicator);
    }
}

function logActivity(type, text) {
    const feed = document.getElementById('activityFeed');
    if (!feed) return;
    const item = document.createElement('div');
    item.className = `activity-item ${type}`;
    item.textContent = text;
    feed.prepend(item);
}

function toggleInputState(disabled) {
    const input = document.getElementById('chatInput');
    if (input) input.disabled = disabled;
    const btn = document.getElementById('sendBtn');
    if (btn) btn.disabled = disabled;
}
