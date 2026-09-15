package ru.korteng.finance_manager.grpc;

import io.grpc.ManagedChannel;
import io.grpc.Server;
import io.grpc.Status;
import io.grpc.StatusRuntimeException;
import io.grpc.inprocess.InProcessChannelBuilder;
import io.grpc.inprocess.InProcessServerBuilder;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import ru.korteng.finance_manager.dto.TransactionResponse;
import jakarta.persistence.EntityNotFoundException;
import ru.korteng.finance_manager.service.TransactionService;

import java.io.IOException;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class TransactionGrpcServiceImplTest {

    private Server server;
    private ManagedChannel channel;
    private TransactionGrpcServiceGrpc.TransactionGrpcServiceBlockingStub stub;
    private TransactionService transactionService;

    @BeforeEach
    void setUp() throws IOException {
        transactionService = mock(TransactionService.class);
        TransactionGrpcServiceImpl service = new TransactionGrpcServiceImpl(transactionService);

        server = InProcessServerBuilder
                .forName("test-server")
                .directExecutor()
                .addService(service)
                .build()
                .start();

        channel = InProcessChannelBuilder
                .forName("test-server")
                .directExecutor()
                .build();

        stub = TransactionGrpcServiceGrpc.newBlockingStub(channel);
    }

    @AfterEach
    void tearDown() throws InterruptedException {
        channel.shutdownNow().awaitTermination(5, TimeUnit.SECONDS);
        server.shutdownNow().awaitTermination(5, TimeUnit.SECONDS);
    }

    @Test
    void getTransaction_returnsResponse() {
        TransactionResponse mockResponse = new TransactionResponse();
        mockResponse.setId(1L);
        mockResponse.setAmount(new BigDecimal("500.00"));
        mockResponse.setCurrency("RUB");
        mockResponse.setCreatedAt(Instant.now());

        when(transactionService.getTransactionById(1L, 1L))
                .thenReturn(mockResponse);

        GetTransactionRequest request = GetTransactionRequest.newBuilder()
                .setId(1L)
                .setUserId(1L)
                .build();

        TransactionGrpcResponse response = stub.getTransaction(request);

        assertEquals(1L, response.getId());
        assertEquals("500.00", response.getAmount());
        assertEquals("RUB", response.getCurrency());
    }

    @Test
    void getTransaction_withCategory_mapsCategoryInfo() {
        TransactionResponse.CategoryInfo categoryInfo = new TransactionResponse.CategoryInfo();
        categoryInfo.setId(10L);
        categoryInfo.setName("Groceries");
        categoryInfo.setParent_id(2L);

        TransactionResponse mockResponse = new TransactionResponse();
        mockResponse.setId(1L);
        mockResponse.setAmount(new BigDecimal("500.00"));
        mockResponse.setCurrency("RUB");
        mockResponse.setCreatedAt(Instant.now());
        mockResponse.setCategory(categoryInfo);

        when(transactionService.getTransactionById(1L, 1L))
                .thenReturn(mockResponse);

        GetTransactionRequest request = GetTransactionRequest.newBuilder()
                .setId(1L)
                .setUserId(1L)
                .build();

        TransactionGrpcResponse response = stub.getTransaction(request);

        assertEquals(10L, response.getCategory().getId());
        assertEquals("Groceries", response.getCategory().getName());
        assertEquals(2L, response.getCategory().getParentId());
    }

    @Test
    void getTransaction_notFound_returnsNotFound() {
        when(transactionService.getTransactionById(999L, 1L))
                .thenThrow(new EntityNotFoundException("Transaction not found"));

        GetTransactionRequest request = GetTransactionRequest.newBuilder()
                .setId(999L)
                .setUserId(1L)
                .build();

        StatusRuntimeException exception = assertThrows(
                StatusRuntimeException.class,
                () -> stub.getTransaction(request)
        );

        assertEquals(Status.Code.NOT_FOUND, exception.getStatus().getCode());
    }

    @Test
    void getTransaction_invalidId_returnsInvalidArgument() {
        GetTransactionRequest request = GetTransactionRequest.newBuilder()
                .setId(-1L)
                .setUserId(1L)
                .build();

        StatusRuntimeException exception = assertThrows(
                StatusRuntimeException.class,
                () -> stub.getTransaction(request)
        );

        assertEquals(Status.Code.INVALID_ARGUMENT, exception.getStatus().getCode());
    }

    @Test
    void getTransaction_invalidUserId_returnsInvalidArgument() {
        GetTransactionRequest request = GetTransactionRequest.newBuilder()
                .setId(1L)
                .setUserId(-1L)
                .build();

        StatusRuntimeException exception = assertThrows(
                StatusRuntimeException.class,
                () -> stub.getTransaction(request)
        );

        assertEquals(Status.Code.INVALID_ARGUMENT, exception.getStatus().getCode());
    }
}