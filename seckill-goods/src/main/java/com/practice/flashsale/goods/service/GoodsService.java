package com.practice.flashsale.goods.service;

import com.practice.flashsale.goods.model.dto.FindSeckillGoodsDetailReqDTO;
import com.practice.flashsale.goods.model.dto.FindSeckillGoodsListReqDTO;
import com.practice.flashsale.goods.model.vo.FindSeckillGoodsDetailRspVO;
import com.practice.flashsale.goods.model.vo.FindSeckillGoodsListRspVO;

import java.util.List;

public interface GoodsService {

    /**
     * 查询秒杀商品列表
     *
     * @param reqDTO
     * @return
     */
    List<FindSeckillGoodsListRspVO> findSeckillGoodsList(FindSeckillGoodsListReqDTO reqDTO);

    /**
     * 查询秒杀商品详情
     *
     * @param dto 入参（活动 ID + 商品 ID）
     * @return 详情
     */
    FindSeckillGoodsDetailRspVO findSeckillGoodsDetail(FindSeckillGoodsDetailReqDTO dto);

}
