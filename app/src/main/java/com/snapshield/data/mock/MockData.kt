package com.snapshield.data.mock

import com.snapshield.data.model.SmsMessage

object MockData {

    val sampleSmsMessages = listOf(
        SmsMessage(
            id = "1",
            sender = "+44 7911 123456",
            text = "URGENT: Your HSBC account has been suspended. Click here to verify: hsbc-secure.xyz/verify",
            timestamp = System.currentTimeMillis() - 3600000
        ),
        SmsMessage(
            id = "2",
            sender = "Amazon",
            text = "Your order #AMZ-2847 has been dispatched. Track your package at amazon.com/track",
            timestamp = System.currentTimeMillis() - 7200000
        ),
        SmsMessage(
            id = "3",
            sender = "+1 555 0123",
            text = "Congratulations! You've won $5,000,000 in the Microsoft Lottery! Click here to claim: ms-lottery.win/claim",
            timestamp = System.currentTimeMillis() - 10800000
        ),
        SmsMessage(
            id = "4",
            sender = "DHL Express",
            text = "Your parcel is waiting for delivery. Please confirm address at dhl.com/delivery",
            timestamp = System.currentTimeMillis() - 14400000
        ),
        SmsMessage(
            id = "5",
            sender = "+91 98765 43210",
            text = "Dear customer, your OTP for transaction is 4829. Do not share with anyone.",
            timestamp = System.currentTimeMillis() - 18000000
        ),
        SmsMessage(
            id = "6",
            sender = "PayPal",
            text = "A payment of $299.99 was made from your account. If this wasn't you, call 1-800-555-0199 immediately",
            timestamp = System.currentTimeMillis() - 21600000
        ),
        SmsMessage(
            id = "7",
            sender = "Netflix",
            text = "Your Netflix subscription will expire tomorrow. Renew now to avoid interruption: netflix-renew.buzz",
            timestamp = System.currentTimeMillis() - 25200000
        ),
        SmsMessage(
            id = "8",
            sender = "+44 7700 900123",
            text = "Hi, are we still meeting for lunch tomorrow at 1pm?",
            timestamp = System.currentTimeMillis() - 28800000
        ),
        SmsMessage(
            id = "9",
            sender = "Royal Mail",
            text = "We attempted delivery but no one was home. Reschedule at royalmail-redelivery.co.uk",
            timestamp = System.currentTimeMillis() - 32400000
        ),
        SmsMessage(
            id = "10",
            sender = "+234 801 2345678",
            text = "DEAR BENEFICIARY, Your inheritance of $4.5M is ready for transfer. Send your details to claim NOW!!!",
            timestamp = System.currentTimeMillis() - 36000000
        )
    )
}
