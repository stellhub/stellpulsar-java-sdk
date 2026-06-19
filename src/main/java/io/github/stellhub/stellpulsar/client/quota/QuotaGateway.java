package io.github.stellhub.stellpulsar.client.quota;

public interface QuotaGateway {

    /**
     * 向 StellPulsar 服务端申请配额。
     */
    QuotaGatewayResponse acquire(AcquireQuotaCommand command);
}
