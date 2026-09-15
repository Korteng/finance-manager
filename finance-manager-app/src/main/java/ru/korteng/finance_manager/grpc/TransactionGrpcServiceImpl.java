package ru.korteng.finance_manager.grpc;

import io.grpc.Status;
import io.grpc.stub.StreamObserver;
import jakarta.persistence.EntityNotFoundException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import net.devh.boot.grpc.server.service.GrpcService;
import ru.korteng.finance_manager.dto.TransactionResponse;
import ru.korteng.finance_manager.service.TransactionService;

/**
 * gRPC counterpart of {@code GET /api/transactions/{id}}. Delegates to the same
 * {@link TransactionService}, so the ownership check that fixed the IDOR issue on the
 * REST endpoint (see finance-manager-fixes.md) applies here identically - there is only
 * one place the "does this transaction belong to this user" rule lives.
 */
@GrpcService
@RequiredArgsConstructor
@Slf4j
public class TransactionGrpcServiceImpl extends TransactionGrpcServiceGrpc.TransactionGrpcServiceImplBase {

    private final TransactionService transactionService;

    @Override
    public void getTransaction(GetTransactionRequest request, StreamObserver<TransactionGrpcResponse> responseObserver) {

        if (request.getId() <= 0) {
            responseObserver.onError(Status.INVALID_ARGUMENT
                    .withDescription("id must be positive")
                    .asRuntimeException());
            return;
        }
        if (request.getUserId() <= 0) {
            responseObserver.onError(Status.INVALID_ARGUMENT
                    .withDescription("userId must be positive")
                    .asRuntimeException());
            return;
        }

        try {
            TransactionResponse transaction = transactionService.getTransactionById(request.getId(), request.getUserId());
            responseObserver.onNext(toGrpcResponse(transaction));
            responseObserver.onCompleted();
        } catch (EntityNotFoundException e) {
            log.info("gRPC GetTransaction: not found, id={}, userId={}", request.getId(), request.getUserId());
            responseObserver.onError(Status.NOT_FOUND
                    .withDescription(e.getMessage())
                    .asRuntimeException());
        } catch (Exception e) {
            log.error("gRPC GetTransaction: unexpected error, id={}", request.getId(), e);
            responseObserver.onError(Status.INTERNAL
                    .withDescription("Internal error")
                    .asRuntimeException());
        }
    }

    private TransactionGrpcResponse toGrpcResponse(TransactionResponse transaction) {
        TransactionGrpcResponse.Builder builder = TransactionGrpcResponse.newBuilder()
                .setId(transaction.getId())
                .setAmount(transaction.getAmount().toPlainString())
                .setCurrency(transaction.getCurrency())
                .setCreatedAt(transaction.getCreatedAt().toString());

        if (transaction.getDescription() != null) {
            builder.setDescription(transaction.getDescription());
        }

        if (transaction.getCategory() != null) {
            CategoryInfo.Builder categoryBuilder = CategoryInfo.newBuilder()
                    .setId(transaction.getCategory().getId())
                    .setName(transaction.getCategory().getName());
            if (transaction.getCategory().getParent_id() != null) {
                categoryBuilder.setParentId(transaction.getCategory().getParent_id());
            }
            builder.setCategory(categoryBuilder.build());
        }

        return builder.build();
    }
}
