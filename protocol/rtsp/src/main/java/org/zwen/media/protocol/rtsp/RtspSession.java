package org.zwen.media.protocol.rtsp;

import java.util.ArrayList;
import java.util.List;

import javax.sdp.MediaDescription;
import javax.sdp.SdpException;
import javax.sdp.SessionDescription;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.biasedbit.efflux.packet.DataPacket;
import com.biasedbit.efflux.participant.RtpParticipantInfo;
import com.biasedbit.efflux.session.RtpSession;
import com.biasedbit.efflux.session.RtpSessionDataListener;

public class RtspSession {
	private static final Logger logger = LoggerFactory
			.getLogger(RtspSession.class);

	private String sessionId;
	private SessionDescription sessionDescription;
	private List<RtpSession> rtpSessions = new ArrayList<RtpSession>();

	public RtspSession() {
	}

	public RtspSession(SessionDescription sd) {
		this.sessionDescription = sd;
	}

	public int getNumRtpSessions() {
		return rtpSessions.size();
	}
	
	@SuppressWarnings("unchecked")
	public int getNumMediaDescriptions() {
		List<MediaDescription> mds;
		try {
			mds = sessionDescription.getMediaDescriptions(false);

			return null == mds ? 0 : mds.size();
		} catch (SdpException e) {
			defaultHandleSdpException(e);
		}

		return 0;
	}

	@SuppressWarnings("unchecked")
	public MediaDescription getMediaDescription(int index) {
		List<MediaDescription> mds;
		try {
			mds = sessionDescription.getMediaDescriptions(false);

			if (null == mds) {
				return null;
			}

			if (0 <= index && index < mds.size()) {
				return mds.get(index);
			} else {
				return null;
			}

		} catch (SdpException e) {
			defaultHandleSdpException(e);
		}

		return null;
	}

	@SuppressWarnings("unchecked")
	public MediaDescription getMediaDescription(String type) {
		try {
			List<MediaDescription> mds = sessionDescription
					.getMediaDescriptions(false);
			if (null == mds) {
				return null;
			}

			for (MediaDescription mediaDescription : mds) {
				if (mediaDescription.getMedia().getMediaType().equals(type)) {
					return mediaDescription;
				}
			}

			logger.info("NotFound Media Type[" + type + "]");
			return null;
		} catch (SdpException e) {
			defaultHandleSdpException(e);
			return null;
		}
	}

	private void defaultHandleSdpException(SdpException e) {
		logger.warn("{}, {}", e.getMessage(), e);
	}

	public void setSessionDescription(SessionDescription sessionDescription) {
		this.sessionDescription = sessionDescription;
	}

	public SessionDescription getSessionDescription() {
		return sessionDescription;
	}

	public void setSessionId(String sessionId) {
		this.sessionId = sessionId;
	}

	public String getSessionId() {
		return sessionId;
	}

	public void addRtpSession(RtpSession rtp) {
		rtpSessions.add(rtp);
		
		rtp.addDataListener(new RtpSessionDataListener() {
			
			@Override
			public void dataPacketReceived(RtpSession session,
					RtpParticipantInfo participant, DataPacket packet) {
				logger.debug(">>> {}, {}", session.getLocalParticipant().getDataDestination(), packet);
			}
		});
	}

	public void init() {
		for (RtpSession rtp : rtpSessions) {
			rtp.init();
		}
	}
}
