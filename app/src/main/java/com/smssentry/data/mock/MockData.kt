package com.smssentry.data.mock

import com.smssentry.data.model.SmsMessage

object MockData {

    private fun hoursAgo(hours: Int): Long = System.currentTimeMillis() - (hours * 3600000L)
    private fun minsAgo(mins: Int): Long = System.currentTimeMillis() - (mins * 60000L)

    val sampleSmsMessages = listOf(
        SmsMessage(
            id = "1",
            sender = "+44 7911 123456",
            text = "URGENT: Your HSBC account has been suspended. Click here to verify: hsbc-secure.xyz/verify",
            timestamp = minsAgo(5)
        ),
        SmsMessage(
            id = "2",
            sender = "Amazon",
            text = "Your order #AMZ-2847 has been dispatched. Track your package at amazon.com/track",
            timestamp = minsAgo(25)
        ),
        SmsMessage(
            id = "3",
            sender = "+1 555 0123",
            text = "Congratulations! You've won $5,000,000 in the Microsoft Lottery! Click here to claim: ms-lottery.win/claim",
            timestamp = minsAgo(45)
        ),
        SmsMessage(
            id = "4",
            sender = "DHL Express",
            text = "Your parcel is waiting for delivery. Please confirm address at dhl.com/delivery",
            timestamp = hoursAgo(1)
        ),
        SmsMessage(
            id = "5",
            sender = "+91 98765 43210",
            text = "Dear customer, your OTP for transaction is 4829. Do not share with anyone.",
            timestamp = hoursAgo(2)
        ),
        SmsMessage(
            id = "6",
            sender = "PayPal",
            text = "A payment of $299.99 was made from your account. If this wasn't you, call 1-800-555-0199 immediately",
            timestamp = hoursAgo(3)
        ),
        SmsMessage(
            id = "7",
            sender = "Netflix",
            text = "Your Netflix subscription will expire tomorrow. Renew now to avoid interruption: netflix-renew.buzz",
            timestamp = hoursAgo(4)
        ),
        SmsMessage(
            id = "8",
            sender = "+44 7700 900123",
            text = "Hi, are we still meeting for lunch tomorrow at 1pm?",
            timestamp = hoursAgo(5)
        ),
        SmsMessage(
            id = "9",
            sender = "Royal Mail",
            text = "We attempted delivery but no one was home. Reschedule at royalmail-redelivery.co.uk",
            timestamp = hoursAgo(6)
        ),
        SmsMessage(
            id = "10",
            sender = "+234 801 2345678",
            text = "DEAR BENEFICIARY, Your inheritance of $4.5M is ready for transfer. Send your details to claim NOW!!!",
            timestamp = hoursAgo(7)
        ),
        SmsMessage(
            id = "11",
            sender = "Amazon",
            text = "Your Amazon refund of $45.99 has been processed. Allow 3-5 business days.",
            timestamp = hoursAgo(8)
        ),
        SmsMessage(
            id = "12",
            sender = "Netflix",
            text = "New arrivals this week: Check out the latest movies and series on Netflix!",
            timestamp = hoursAgo(9)
        ),
        SmsMessage(
            id = "13",
            sender = "+44 7911 123456",
            text = "Your HSBC card ending 4821 was used for £299.00 at TESCO. If not you, call us.",
            timestamp = hoursAgo(10)
        ),
        SmsMessage(
            id = "14",
            sender = "Amazon",
            text = "Your package has been delivered to your doorstep. Thank you for shopping with us!",
            timestamp = hoursAgo(11)
        ),
        SmsMessage(
            id = "15",
            sender = "+1 555 0123",
            text = "FREE iPhone 15! You have been selected! Claim now at free-phone.win/claim",
            timestamp = hoursAgo(12)
        ),
        SmsMessage(
            id = "16",
            sender = "PayPal",
            text = "You've received a payment of $50.00 from john.doe@email.com",
            timestamp = hoursAgo(13)
        ),
        SmsMessage(
            id = "17",
            sender = "DHL Express",
            text = "Your shipment tracking number is DHL-928374. View status at dhl.com/track",
            timestamp = hoursAgo(14)
        ),
        SmsMessage(
            id = "18",
            sender = "+44 7700 900123",
            text = "Running 10 mins late, see you soon!",
            timestamp = hoursAgo(15)
        ),
        SmsMessage(
            id = "19",
            sender = "Netflix",
            text = "Your password was changed. If you didn't do this, secure your account: netflix-security.com",
            timestamp = hoursAgo(16)
        ),
        SmsMessage(
            id = "20",
            sender = "+91 98765 43210",
            text = "Your bank account has been credited with Rs. 15,000. Available balance: Rs. 42,350",
            timestamp = hoursAgo(17)
        ),
        SmsMessage(
            id = "21",
            sender = "Royal Mail",
            text = "A parcel is being delivered today. Track: royalmail.com/track/RM4829103UK",
            timestamp = hoursAgo(18)
        ),
        SmsMessage(
            id = "22",
            sender = "Amazon",
            text = "Deal of the Day: Up to 70% off on electronics. Shop now at amazon.com/deals",
            timestamp = hoursAgo(20)
        ),
        SmsMessage(
            id = "23",
            sender = "+44 7911 123456",
            text = "Your account statement for May 2026 is ready. View at hsbc.co.uk/statements",
            timestamp = hoursAgo(22)
        ),
        SmsMessage(
            id = "24",
            sender = "PayPal",
            text = "Your monthly statement is ready. Log in to view your transactions.",
            timestamp = hoursAgo(24)
        ),
        SmsMessage(
            id = "25",
            sender = "+234 801 2345678",
            text = "CONGRATULATIONS! You have won a brand new Mercedes-Benz! Send your name and address to claim.",
            timestamp = hoursAgo(26)
        ),
        SmsMessage(
            id = "26",
            sender = "Netflix",
            text = "Enjoying your show? Rate it and help others discover great content!",
            timestamp = hoursAgo(28)
        ),
        SmsMessage(
            id = "27",
            sender = "+1 555 0123",
            text = "URGENT: Your Social Security number has been compromised. Call 1-800-555-0199 NOW",
            timestamp = hoursAgo(30)
        ),
        SmsMessage(
            id = "28",
            sender = "Amazon",
            text = "Review your recent purchase: Wireless Earbuds. Your feedback helps other customers.",
            timestamp = hoursAgo(32)
        ),
        SmsMessage(
            id = "29",
            sender = "DHL Express",
            text = "Customs clearance required for your package. Pay duties at dhl.com/customs",
            timestamp = hoursAgo(34)
        ),
        SmsMessage(
            id = "30",
            sender = "+44 7700 900123",
            text = "Happy birthday! Hope you have an amazing day!",
            timestamp = hoursAgo(36)
        )
    )
}
