package com.bankmanagement.bankmanagement.service;

import com.bankmanagement.bankmanagement.dto.TransactionRequestDTO;
import com.bankmanagement.bankmanagement.dto.TransactionResponseDTO;
import com.bankmanagement.bankmanagement.exception.BadRequestException;
import com.bankmanagement.bankmanagement.exception.NotFoundException;
import com.bankmanagement.bankmanagement.model.Account;
import com.bankmanagement.bankmanagement.model.Transaction;
import com.bankmanagement.bankmanagement.model.TransactionType;
import com.bankmanagement.bankmanagement.repository.AccountRepository;
import com.bankmanagement.bankmanagement.repository.TransactionRepository;
import com.bankmanagement.bankmanagement.service.impl.TransactionServiceImpl;
import com.bankmanagement.bankmanagement.service.strategy.ATMDepositStrategy;
import com.bankmanagement.bankmanagement.service.strategy.ATMWithdrawalStrategy;
import com.bankmanagement.bankmanagement.service.strategy.TransactionStrategyFactory;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
public class TransactionServiceTest {

    @Mock
    private TransactionRepository transactionRepository;

    @Mock
    private AccountRepository accountRepository;

    @Mock
    private TransactionStrategyFactory strategyFactory;

    @InjectMocks
    private TransactionServiceImpl transactionService;

    @Mock
    private ATMDepositStrategy atmDepositStrategy;

    @Mock
    private ATMWithdrawalStrategy atmWithdrawalStrategy;

    @BeforeEach
    void setUp() {
        transactionService = new TransactionServiceImpl(transactionRepository, accountRepository, strategyFactory);
    }

    @Test
    void shouldCreateATMDepositTransactionSuccessfully() {
        TransactionRequestDTO transactionRequest = new TransactionRequestDTO(
                100.0,
                TransactionType.ATM_DEPOSIT,
                "12345678"
        );

        Account account = new Account("675e0e4a59d6de4eda5b29b8", "12345678", 500.0, "675e0e1259d6de4eda5b29b7");

        Transaction savedTransaction = new Transaction(
                "675e0ec661737976b43cca85",
                100.0,
                2.0,
                98.0,
                TransactionType.ATM_DEPOSIT,
                LocalDateTime.now(),
                "675e0e4a59d6de4eda5b29b8"
        );

        when(accountRepository.findByAccountNumber(transactionRequest.getAccountNumber()))
                .thenReturn(Mono.just(account));
        when(atmDepositStrategy.calculateFee()).thenReturn(2.0);
        when(atmDepositStrategy.calculateBalance(account.getBalance(), transactionRequest.getAmount()))
                .thenReturn(598.0);
        when(strategyFactory.getStrategy(transactionRequest.getType())).thenReturn(atmDepositStrategy);
        when(transactionRepository.save(any(Transaction.class))).thenReturn(Mono.just(savedTransaction));
        when(accountRepository.save(account)).thenReturn(Mono.just(account));

        Mono<TransactionResponseDTO> responseMono = transactionService.create(transactionRequest);

        StepVerifier.create(responseMono)
                .assertNext(response -> {
                    assertNotNull(response);
                    assertEquals(98.0, response.getNetAmount());
                    assertEquals(2.0, response.getFee());
                    assertEquals(TransactionType.ATM_DEPOSIT, response.getType());
                })
                .verifyComplete();

        verify(accountRepository, times(1)).findByAccountNumber(transactionRequest.getAccountNumber());
        verify(strategyFactory, times(1)).getStrategy(TransactionType.ATM_DEPOSIT);
        verify(atmDepositStrategy, times(1)).calculateFee();
        verify(atmDepositStrategy, times(1)).calculateBalance(500.0, transactionRequest.getAmount());
        verify(accountRepository, times(1)).save(account);
        verify(transactionRepository, times(1)).save(any(Transaction.class));
    }

    @Test
    void shouldCreateATMWithdrawalTransactionSuccessfully() {
        TransactionRequestDTO transactionRequest = new TransactionRequestDTO(
                50.0,
                TransactionType.ATM_WITHDRAWAL,
                "12345678"
        );

        Account account = new Account("675e0e4a59d6de4eda5b29b8", "12345678", 500.0, "675e0e1259d6de4eda5b29b7");

        Transaction savedTransaction = new Transaction(
                "675e0ec661737976b43cca85",
                50.0,
                1.0,
                49.0,
                TransactionType.ATM_WITHDRAWAL,
                LocalDateTime.now(),
                "675e0e4a59d6de4eda5b29b8"
        );

        when(accountRepository.findByAccountNumber(transactionRequest.getAccountNumber()))
                .thenReturn(Mono.just(account));
        when(strategyFactory.getStrategy(transactionRequest.getType())).thenReturn(atmWithdrawalStrategy);
        when(atmWithdrawalStrategy.calculateFee()).thenReturn(1.0);
        when(atmWithdrawalStrategy.calculateBalance(account.getBalance(), transactionRequest.getAmount()))
                .thenReturn(449.0);
        when(transactionRepository.save(any(Transaction.class))).thenReturn(Mono.just(savedTransaction));
        when(accountRepository.save(account)).thenReturn(Mono.just(account));

        Mono<TransactionResponseDTO> responseMono = transactionService.create(transactionRequest);

        StepVerifier.create(responseMono)
                .assertNext(response -> {
                    assertNotNull(response);
                    assertEquals(49.0, response.getNetAmount());
                    assertEquals(1.0, response.getFee());
                    assertEquals(TransactionType.ATM_WITHDRAWAL, response.getType());
                })
                .verifyComplete();

        verify(accountRepository, times(1)).findByAccountNumber(transactionRequest.getAccountNumber());
        verify(strategyFactory, times(1)).getStrategy(TransactionType.ATM_WITHDRAWAL);
        verify(atmWithdrawalStrategy, times(1)).calculateFee();
        verify(atmWithdrawalStrategy, times(1)).calculateBalance(500.0, transactionRequest.getAmount());
        verify(accountRepository, times(1)).save(account);
        verify(transactionRepository, times(1)).save(any(Transaction.class));
    }

    @Test
    void shouldThrowExceptionWhenInsufficientFundsForATMWithdrawal() {
        TransactionRequestDTO transactionRequest = new TransactionRequestDTO(
                600.0,
                TransactionType.ATM_WITHDRAWAL,
                "12345678"
        );

        Account account = new Account("675e0e4a59d6de4eda5b29b8", "12345678", 500.0, "675e0e1259d6de4eda5b29b7");

        when(accountRepository.findByAccountNumber(transactionRequest.getAccountNumber()))
                .thenReturn(Mono.just(account));
        when(strategyFactory.getStrategy(transactionRequest.getType()))
                .thenReturn(atmWithdrawalStrategy);
        when(atmWithdrawalStrategy.calculateFee())
                .thenReturn(1.0);
        when(atmWithdrawalStrategy.calculateBalance(account.getBalance(), transactionRequest.getAmount()))
                .thenThrow(new BadRequestException("Insufficient balance for this transaction."));

        Mono<TransactionResponseDTO> responseMono = transactionService.create(transactionRequest);

        StepVerifier.create(responseMono)
                .expectErrorMatches(ex -> ex instanceof BadRequestException && ex.getMessage().equals("Insufficient balance for this transaction."))
                .verify();

        verify(accountRepository, times(1)).findByAccountNumber(transactionRequest.getAccountNumber());
        verify(strategyFactory, times(1)).getStrategy(TransactionType.ATM_WITHDRAWAL);
        verify(atmWithdrawalStrategy, times(1)).calculateFee();
        verify(atmWithdrawalStrategy, times(1)).calculateBalance(500.0, transactionRequest.getAmount());
        verify(accountRepository, never()).save(any(Account.class));
        verify(transactionRepository, never()).save(any(Transaction.class));
    }

    @Test
    void shouldReturnTransactionsListForAccountNumber() {
        String accountNumber = "12345678";

        Account account = new Account("675e0e4a59d6de4eda5b29b8", "12345678", 500.0, "675e0e1259d6de4eda5b29b7");

        List<Transaction> transactions = List.of(
                new Transaction(
                        "675e0ec661737976b43cca85",
                        100.0,
                        2.0,
                        98.0,
                        TransactionType.ATM_DEPOSIT,
                        LocalDateTime.now(),
                        "675e0e4a59d6de4eda5b29b8"
                ),
                new Transaction(
                        "675e0ec661737976b43cca86",
                        50.0,
                        1.0,
                        49.0,
                        TransactionType.ATM_WITHDRAWAL,
                        LocalDateTime.now(),
                        "675e0e4a59d6de4eda5b29b8"
                )
        );

        when(accountRepository.findByAccountNumber(accountNumber)).thenReturn(Mono.just(account));
        when(transactionRepository.findAllByAccountId(account.getId())).thenReturn(Flux.fromIterable(transactions));

        Flux<TransactionResponseDTO> responseFlux = transactionService.getAllByAccountNumber(accountNumber);

        StepVerifier.create(responseFlux)
                .assertNext(response -> assertEquals("675e0ec661737976b43cca85", response.getId()))
                .assertNext(response -> assertEquals("675e0ec661737976b43cca86", response.getId()))
                .verifyComplete();

        verify(accountRepository, times(1)).findByAccountNumber(accountNumber);
        verify(transactionRepository, times(1)).findAllByAccountId(account.getId());
    }

    @Test
    void shouldThrowExceptionWhenAccountNotFoundByAccountNumber() {
        String accountNumber = "675e0e4a59d6de4eda5b29b3";

        when(accountRepository.findByAccountNumber(accountNumber)).thenReturn(Mono.empty());

        Flux<TransactionResponseDTO> responseMono = transactionService.getAllByAccountNumber(accountNumber);

        StepVerifier.create(responseMono)
                .expectErrorMatches(ex -> ex instanceof NotFoundException && ex.getMessage().equals("Account not found"))
                .verify();

        verify(accountRepository, times(1)).findByAccountNumber(accountNumber);
    }
}
