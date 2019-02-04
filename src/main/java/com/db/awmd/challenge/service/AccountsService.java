package com.db.awmd.challenge.service;

import java.math.BigDecimal;
import java.util.Arrays;
import java.util.concurrent.TimeUnit;

import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import com.db.awmd.challenge.domain.Account;
import com.db.awmd.challenge.exception.InsufficientFundsException;
import com.db.awmd.challenge.exception.InvalidAccountException;
import com.db.awmd.challenge.repository.AccountsRepository;

import lombok.Getter;
import lombok.extern.slf4j.Slf4j;

@Service
@Slf4j
public class AccountsService {

	@Getter
	private final AccountsRepository accountsRepository;

	private NotificationService notificationService;

	//Can be configure in yml.
	private static final long TIME_OUT = 2000L;

	private static final TimeUnit TIME_UNIT_MILISECONDS = TimeUnit.MILLISECONDS;

	@Autowired
	public AccountsService(AccountsRepository accountsRepository) {
		this.accountsRepository = accountsRepository;
	}

	public void createAccount(Account account) {
		this.accountsRepository.createAccount(account);
	}

	public Account getAccount(String accountId) {
		return this.accountsRepository.getAccount(accountId);
	}

	public boolean transferAmt(final String fromAccountId, final String toAccountId, BigDecimal amount)
			throws InsufficientFundsException, InvalidAccountException, InterruptedException {

		if (StringUtils.isNotEmpty(fromAccountId) && StringUtils.isNotEmpty(toAccountId) && amount.compareTo(BigDecimal.ZERO) == 1) {

			Account fromAcct = this.getAccount(fromAccountId);
			Account toAcct = this.getAccount(toAccountId);

			if (fromAcct.getBalance().compareTo(amount) < 0)
			{
				throw new InsufficientFundsException("Available balance is less that amount to transfer" + amount);
			}
			else {

				if (fromAcct.getLock().tryLock(TIME_OUT, TIME_UNIT_MILISECONDS)) {
					try {
						if (toAcct.getLock().tryLock(TIME_OUT, TIME_UNIT_MILISECONDS)) {
							try {
								fromAcct.setBalance(fromAcct.getBalance().subtract(amount));
								toAcct.setBalance(toAcct.getBalance().add(amount));
								log.info("Amount {} successfuly transfered from account {} to acoount {}",amount,fromAcct.getAccountId(),toAcct.getAccountId());
								notificationService.notifyAboutTransfer(fromAcct,
										"Account " + fromAcct.getAccountId() + " debited with amount " + amount);
								notificationService.notifyAboutTransfer(toAcct,
										"Account " + toAcct.getAccountId() + " debited with amount " + amount);

								return true;

							} finally {
								toAcct.getLock().unlock();
							}
						}
					} finally {
						fromAcct.getLock().unlock();
					}
				}
				
			}
		}
		return false;
	}

}
