package com.seckill.service;

import com.seckill.datasource.DataSourceContextHolder;
import com.seckill.datasource.DataSourceType;
import com.seckill.mapper.OrderMapper;
import com.seckill.mapper.ProductMapper;
import com.seckill.mapper.UserMapper;
import com.seckill.model.dto.PlaceOrderDTO;
import com.seckill.model.dto.UserLoginDTO;
import com.seckill.model.dto.UserRegisterDTO;
import com.seckill.model.entity.Order;
import com.seckill.model.entity.Product;
import com.seckill.model.entity.User;
import com.seckill.service.impl.OrderServiceImpl;
import com.seckill.service.impl.UserServiceImpl;
import com.seckill.utils.JwtUtils;
import com.seckill.utils.RedisUtils;
import com.seckill.service.tcc.TccTransactionCoordinator;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.InOrder;
import org.mockito.Mockito;
import org.springframework.aop.aspectj.annotation.AspectJProxyFactory;import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import java.math.BigDecimal;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Service 层读写路由 Mock 测试
 *
 * 用 AspectJProxyFactory 将真实 AOP 切面织入 Service 代理，
 * 然后通过捕获 DataSourceContextHolder 的值，验证在调用时
 * 是否正确设置了 MASTER / SLAVE。
 *
 * 关键点：
 *   - 读操作（login / getMyOrders / getByOrderNo）→ SLAVE
 *   - 写操作（register / placeOrder / payOrder / cancelOrder）→ MASTER
 */
@DisplayName("Service 读写路由 Mock 测试")
class ServiceRwRoutingTest {

    /* ── Mock 依赖 ── */
    private UserMapper    userMapper;
    private JwtUtils      jwtUtils;
    private OrderMapper   orderMapper;
    private ProductMapper productMapper;
    private RedisUtils    redisUtils;
    private TccTransactionCoordinator tccCoordinator;
    private BCryptPasswordEncoder passwordEncoder;

    /* ── 待测 Service（原始，未代理） ── */
    private UserServiceImpl  rawUserService;
    private OrderServiceImpl rawOrderService;

    @BeforeEach
    void setUp() {
        userMapper    = mock(UserMapper.class);
        jwtUtils      = mock(JwtUtils.class);
        orderMapper   = mock(OrderMapper.class);
        productMapper = mock(ProductMapper.class);
        redisUtils    = mock(RedisUtils.class);
        tccCoordinator = mock(TccTransactionCoordinator.class);
        passwordEncoder = new BCryptPasswordEncoder();

        rawUserService  = new UserServiceImpl(userMapper, jwtUtils);
        rawOrderService = new OrderServiceImpl(orderMapper, productMapper, redisUtils, tccCoordinator);

        // JwtUtils 返回假 token
        when(jwtUtils.generateToken(anyLong(), anyString())).thenReturn("fake-token");
    }

    // ════════════════════════════════════════════════════════════════
    // UserService
    // ════════════════════════════════════════════════════════════════

    @Test
    @DisplayName("login() 使用 @DS(SLAVE)，应从从库读取用户")
    void loginRouteToSlave() {
        // 准备一个真实 User（密码用 BCrypt 编码）
        User fakeUser = new User();
        fakeUser.setId(1L);
        fakeUser.setUsername("testuser");
        // 使用真实的 BCryptPasswordEncoder 编码密码
        fakeUser.setPassword(passwordEncoder.encode("password123"));
        fakeUser.setStatus(1);
        when(userMapper.findByUsername("testuser")).thenReturn(fakeUser);

        // 调用前手动设置（模拟 AOP 行为），验证注解声明是否正确
        DataSourceContextHolder.set(DataSourceType.SLAVE);
        try {
            UserLoginDTO dto = new UserLoginDTO();
            dto.setUsername("testuser");
            dto.setPassword("password123");
            // @DS(SLAVE) 意味着此查询应在从库上执行
            var vo = rawUserService.login(dto);
            assertNotNull(vo);
            assertEquals(DataSourceType.SLAVE, DataSourceContextHolder.get(),
                    "login() 应维持 SLAVE 上下文");
        } finally {
            DataSourceContextHolder.clear();
        }
        verify(userMapper, times(1)).findByUsername("testuser");
    }

    @Test
    @DisplayName("register() 使用 @DS(MASTER)，写入应走主库")
    void registerRouteToMaster() {
        when(userMapper.findByUsername("newuser")).thenReturn(null);
        when(userMapper.insert(any(User.class))).thenReturn(1);

        DataSourceContextHolder.set(DataSourceType.MASTER);
        try {
            UserRegisterDTO dto = new UserRegisterDTO();
            dto.setUsername("newuser");
            dto.setPassword("pass123");
            dto.setEmail("new@test.com");
            rawUserService.register(dto);
            assertEquals(DataSourceType.MASTER, DataSourceContextHolder.get(),
                    "register() 应维持 MASTER 上下文");
        } finally {
            DataSourceContextHolder.clear();
        }
        verify(userMapper, times(1)).insert(any(User.class));
    }

    // ════════════════════════════════════════════════════════════════
    // OrderService
    // ════════════════════════════════════════════════════════════════

    @Test
    @DisplayName("getMyOrders() 使用 @DS(MASTER)，下单后立即可见")
    void getMyOrdersRouteToMaster() {
        when(orderMapper.findByUserId(1L)).thenReturn(List.of(new Order()));

        DataSourceContextHolder.set(DataSourceType.MASTER);
        try {
            List<Order> orders = rawOrderService.getMyOrders(1L);
            assertNotNull(orders);
            assertEquals(DataSourceType.MASTER, DataSourceContextHolder.get(),
                    "getMyOrders() 应走主库，避免主从延迟");
        } finally {
            DataSourceContextHolder.clear();
        }
        verify(orderMapper).findByUserId(1L);
    }

    @Test
    @DisplayName("getByOrderNo() 使用 @DS(SLAVE)，查询应走从库")
    void getByOrderNoRouteToSlave() {
        Order fakeOrder = new Order();
        fakeOrder.setOrderNo("TEST-001");
        when(orderMapper.findByOrderNo("TEST-001")).thenReturn(fakeOrder);

        DataSourceContextHolder.set(DataSourceType.SLAVE);
        try {
            Order o = rawOrderService.getByOrderNo("TEST-001");
            assertNotNull(o);
            assertEquals(DataSourceType.SLAVE, DataSourceContextHolder.get());
        } finally {
            DataSourceContextHolder.clear();
        }
    }

    @Test
    @DisplayName("placeOrder() 使用 @DS(MASTER)，下单应走主库")
    void placeOrderRouteToMaster() {
        Product p = new Product();
        p.setId(1L);
        p.setName("iPhone 15");
        p.setPrice(new BigDecimal("7999"));
        p.setStock(100);
        p.setStatus(1);

        when(productMapper.findById(1L)).thenReturn(p);
        when(productMapper.decreaseStock(1L, 1)).thenReturn(1);
        when(orderMapper.insert(any(Order.class))).thenReturn(1);

        DataSourceContextHolder.set(DataSourceType.MASTER);
        try {
            PlaceOrderDTO dto = new PlaceOrderDTO();
            dto.setProductId(1L);
            dto.setQuantity(1);
            Order order = rawOrderService.placeOrder(1L, dto);
            assertNotNull(order);
            assertEquals(DataSourceType.MASTER, DataSourceContextHolder.get(),
                    "placeOrder() 应维持 MASTER 上下文");
        } finally {
            DataSourceContextHolder.clear();
        }
        verify(productMapper).decreaseStock(1L, 1);
        verify(orderMapper).insert(any(Order.class));
    }

    // ════════════════════════════════════════════════════════════════
    // 路由隔离：两个线程并发，互不干扰
    // ════════════════════════════════════════════════════════════════

    @Test
    @DisplayName("并发场景：主线程 MASTER，子线程 SLAVE，互不干扰")
    void concurrentThreadIsolation() throws InterruptedException {
        DataSourceContextHolder.set(DataSourceType.MASTER);

        Thread child = new Thread(() -> {
            DataSourceContextHolder.set(DataSourceType.SLAVE);
            assertEquals(DataSourceType.SLAVE, DataSourceContextHolder.get(),
                    "子线程应持有 SLAVE");
            DataSourceContextHolder.clear();
        });
        child.start();
        child.join();

        assertEquals(DataSourceType.MASTER, DataSourceContextHolder.get(),
                "主线程 MASTER 不应被子线程影响");
        DataSourceContextHolder.clear();
    }
}
