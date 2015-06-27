package org.zwen.media.codec.video.h264;

import java.nio.ByteBuffer;

import org.jcodec.codecs.h264.io.model.NALUnit;
import org.jcodec.codecs.h264.io.model.NALUnitType;
import org.jcodec.codecs.h264.io.model.SeqParameterSet;
import org.zwen.media.AVStreamExtra;
import org.zwen.media.ByteBuffers;

public class H264Extra implements AVStreamExtra {
	private ByteBuffer profile;
	private ByteBuffer sps;
	private ByteBuffer pps;
	private int width;
	private int height;
	
	public void setProfile(ByteBuffer profile) {
		this.profile = profile;
	}
	
	public void addPps(ByteBuffer pps) {
		if (null != pps) {
			
			NALUnit unit = readNALUnit(pps);
			if (unit.type != NALUnitType.PPS) {
				throw new IllegalArgumentException("NALUnitType is NOT PPS");
			}
			this.pps = ByteBuffers.copy(pps);
		}
	}
	
		
	public void addSps(ByteBuffer sps) {
		if (null != sps) {
			NALUnit unit = readNALUnit(sps);
			if (unit.type != NALUnitType.SPS) {
				throw new IllegalArgumentException("NALUnitType is NOT SPS");
			}
			this.sps = ByteBuffers.copy(sps);
			
			sps.get();// ignore na type
			SeqParameterSet set = SeqParameterSet.read(sps);
			this.width = getWidth(set);
			this.height = getHeight(set);
			
			if (null == this.profile) {
				ByteBuffer profile = ByteBuffer.allocate(3);
				profile.put(sps.get(1));
				profile.put(sps.get(2));
				profile.put(sps.get(3));
				profile.flip();
				
				this.profile = profile;
			}
		}
	}
	
	private NALUnit readNALUnit(ByteBuffer buf) {
		buf.mark();
		NALUnit unit = NALUnit.read(buf);
		buf.reset();
		
		return unit;
	}
	

    
	public ByteBuffer readSps() {
		return null != sps ? sps.duplicate() : null;
	}
	
	public ByteBuffer readPps() {
		return null != pps ? pps.duplicate() : null;
	}

	public ByteBuffer readProfile() {
		return null != profile ? profile.duplicate() : null;
	}
	
    public int getWidth() {
		return width;
	}

	public int getHeight() {
		return height;
	}

	/**
     * 获取 h264 视频宽
     * @return -1 表示未知
     * @since V1.0 2014-12-7
     * @author chenxh
     */
    public static int getWidth(SeqParameterSet sps) {

        int width = -1;
        if (sps.frame_cropping_flag) {
            width =
                    ((sps.pic_width_in_mbs_minus1 + 1) * 16) - sps.frame_crop_left_offset * 2
                            - sps.frame_crop_right_offset * 2;
        } else {
            width = ((sps.pic_width_in_mbs_minus1 + 1) * 16);
        }
        return width;

    }

    /**
     * 获取视频高
     * @return -1 表示未知
     * @since V1.0 2014-12-7
     * @author chenxh
     */
    public static int getHeight(SeqParameterSet sps) {

        int height = -1;
        height =
                ((2 - (sps.frame_mbs_only_flag ? 1 : 0)) * (sps.pic_height_in_map_units_minus1 + 1) * 16)
                        - (sps.frame_crop_top_offset * 2) - (sps.frame_crop_bottom_offset * 2);

        return height;
    }

}
