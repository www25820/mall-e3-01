package com.macro.mall.portal;

import com.macro.mall.mapper.*;
import com.macro.mall.model.*;
import com.macro.mall.portal.dao.SmsCouponHistoryDao;
import com.macro.mall.portal.domain.CartPromotionItem;
import com.macro.mall.portal.domain.SmsCouponHistoryDetail;
import com.macro.mall.portal.service.UmsMemberService;
import com.macro.mall.portal.service.impl.UmsMemberCouponServiceImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.*;

/**
 * E3-01 优惠券金额门槛精度 —— 规格测试（测试先行）。
 * <p>
 * 业务不变量：门槛 100 时，购物车金额须「大于等于」100 才可用；99.90 元虽接近但不足，
 * 必须不可用。缺陷根因是 {@code totalAmount.subtract(minPoint).intValue() >= 0} 把差额
 * (-1,0) 截断为 0，导致 99.90 被误判为达到 100。
 * <p>
 * 本测试用 Mockito 隔离，只注入 {@link UmsMemberService} 与 {@link SmsCouponHistoryDao}，
 * 其余 mapper 无需 mock（listCart 路径不触碰它们）。
 */
public class CouponMinPointPrecisionTest {

    @Mock
    private UmsMemberService memberService;
    @Mock
    private SmsCouponHistoryDao couponHistoryDao;

    @InjectMocks
    private UmsMemberCouponServiceImpl service;

    @BeforeEach
    public void setUp() {
        MockitoAnnotations.openMocks(this);
        // @InjectMocks 会把 memberService / couponHistoryDao 注入 service；
        // 其它 mapper 字段在本路径不参与，保持 null 即可
    }

    private UmsMember member(Long id) {
        UmsMember m = new UmsMember();
        m.setId(id);
        return m;
    }

    private CartPromotionItem cartItem(Long productId, Long categoryId, String price, int qty) {
        CartPromotionItem item = new CartPromotionItem();
        item.setProductId(productId);
        item.setProductCategoryId(categoryId);
        item.setPrice(new BigDecimal(price));
        item.setReduceAmount(new BigDecimal("0"));
        item.setQuantity(qty);
        return item;
    }

    /** 构造一个「未过期、指定 useType/minPoint」的优惠券详情 */
    private SmsCouponHistoryDetail couponDetail(Integer useType, String minPoint) {
        SmsCoupon coupon = new SmsCoupon();
        coupon.setUseType(useType);
        coupon.setMinPoint(new BigDecimal(minPoint));
        coupon.setEndTime(new Date(System.currentTimeMillis() + 3600_000L)); // 未来1小时，未过期
        SmsCouponHistoryDetail detail = new SmsCouponHistoryDetail();
        detail.setCoupon(coupon);
        detail.setProductRelationList(new ArrayList<>());
        detail.setCategoryRelationList(new ArrayList<>());
        return detail;
    }

    // ============ 全场券（useType=0） ============

    @Test
    public void wholeStoreBelowThresholdRejected() {
        // 98.90 元，低于 100 门槛 -> 不可用（进入 disableList）
        when(memberService.getCurrentMember()).thenReturn(member(1L));
        List<CartPromotionItem> cart = Collections.singletonList(cartItem(1L, 1L, "98.90", 1));
        SmsCouponHistoryDetail d = couponDetail(0, "100");
        when(couponHistoryDao.getDetailList(1L)).thenReturn(Collections.singletonList(d));

        List<SmsCouponHistoryDetail> enable = service.listCart(cart, 1);
        assertEquals(0, enable.size(), "98.90 低于 100 门槛，全场券应不可用");
    }

    @Test
    public void wholeStoreNinetyNineNinetyRejected() {
        // 99.90 元，缺陷点：-0.10 被 intValue 截断为 0，误判为达到门槛
        when(memberService.getCurrentMember()).thenReturn(member(1L));
        List<CartPromotionItem> cart = Collections.singletonList(cartItem(1L, 1L, "99.90", 1));
        SmsCouponHistoryDetail d = couponDetail(0, "100");
        when(couponHistoryDao.getDetailList(1L)).thenReturn(Collections.singletonList(d));

        List<SmsCouponHistoryDetail> enable = service.listCart(cart, 1);
        assertEquals(0, enable.size(), "99.90 低于 100 门槛，全场券必须不可用（缺陷：intValue 截断）");
    }

    @Test
    public void wholeStoreExactlyAtThresholdAccepted() {
        when(memberService.getCurrentMember()).thenReturn(member(1L));
        List<CartPromotionItem> cart = Collections.singletonList(cartItem(1L, 1L, "100.00", 1));
        SmsCouponHistoryDetail d = couponDetail(0, "100");
        when(couponHistoryDao.getDetailList(1L)).thenReturn(Collections.singletonList(d));

        List<SmsCouponHistoryDetail> enable = service.listCart(cart, 1);
        assertEquals(1, enable.size(), "100.00 恰好达到门槛，全场券应可用");
    }

    @Test
    public void wholeStoreAboveThresholdAccepted() {
        when(memberService.getCurrentMember()).thenReturn(member(1L));
        List<CartPromotionItem> cart = Collections.singletonList(cartItem(1L, 1L, "100.10", 1));
        SmsCouponHistoryDetail d = couponDetail(0, "100");
        when(couponHistoryDao.getDetailList(1L)).thenReturn(Collections.singletonList(d));

        List<SmsCouponHistoryDetail> enable = service.listCart(cart, 1);
        assertEquals(1, enable.size(), "100.10 高于门槛，全场券应可用");
    }

    // ============ 分类券（useType=1） ============

    @Test
    public void categoryNinetyNineNinetyRejected() {
        when(memberService.getCurrentMember()).thenReturn(member(1L));
        List<CartPromotionItem> cart = Collections.singletonList(cartItem(1L, 100L, "99.90", 1));
        SmsCouponHistoryDetail d = couponDetail(1, "100");
        SmsCouponProductCategoryRelation rel = new SmsCouponProductCategoryRelation();
        rel.setProductCategoryId(100L);
        d.setCategoryRelationList(Collections.singletonList(rel));
        when(couponHistoryDao.getDetailList(1L)).thenReturn(Collections.singletonList(d));

        List<SmsCouponHistoryDetail> enable = service.listCart(cart, 1);
        assertEquals(0, enable.size(), "分类券：99.90 低于 100 门槛必须不可用");
    }

    // ============ 商品券（useType=2） ============

    @Test
    public void productNinetyNineNinetyRejected() {
        when(memberService.getCurrentMember()).thenReturn(member(1L));
        List<CartPromotionItem> cart = Collections.singletonList(cartItem(9L, 1L, "99.90", 1));
        SmsCouponHistoryDetail d = couponDetail(2, "100");
        SmsCouponProductRelation rel = new SmsCouponProductRelation();
        rel.setProductId(9L);
        d.setProductRelationList(Collections.singletonList(rel));
        when(couponHistoryDao.getDetailList(1L)).thenReturn(Collections.singletonList(d));

        List<SmsCouponHistoryDetail> enable = service.listCart(cart, 1);
        assertEquals(0, enable.size(), "商品券：99.90 低于 100 门槛必须不可用");
    }

    // ============ 过期券 ============

    @Test
    public void expiredCouponRejected() {
        when(memberService.getCurrentMember()).thenReturn(member(1L));
        List<CartPromotionItem> cart = Collections.singletonList(cartItem(1L, 1L, "200.00", 1));
        SmsCouponHistoryDetail d = couponDetail(0, "100");
        d.getCoupon().setEndTime(new Date(System.currentTimeMillis() - 3600_000L)); // 已过期
        when(couponHistoryDao.getDetailList(1L)).thenReturn(Collections.singletonList(d));

        List<SmsCouponHistoryDetail> enable = service.listCart(cart, 1);
        assertEquals(0, enable.size(), "过期券即使金额达标也不可用");
    }
}
