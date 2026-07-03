-- MySQL dump 10.13  Distrib 8.0.46, for Win64 (x86_64)
--
-- Host: localhost    Database: safepath
-- ------------------------------------------------------
-- Server version	8.0.46

/*!40101 SET @OLD_CHARACTER_SET_CLIENT=@@CHARACTER_SET_CLIENT */;
/*!40101 SET @OLD_CHARACTER_SET_RESULTS=@@CHARACTER_SET_RESULTS */;
/*!40101 SET @OLD_COLLATION_CONNECTION=@@COLLATION_CONNECTION */;
/*!50503 SET NAMES utf8mb4 */;
/*!40103 SET @OLD_TIME_ZONE=@@TIME_ZONE */;
/*!40103 SET TIME_ZONE='+00:00' */;
/*!40014 SET @OLD_UNIQUE_CHECKS=@@UNIQUE_CHECKS, UNIQUE_CHECKS=0 */;
/*!40014 SET @OLD_FOREIGN_KEY_CHECKS=@@FOREIGN_KEY_CHECKS, FOREIGN_KEY_CHECKS=0 */;
/*!40101 SET @OLD_SQL_MODE=@@SQL_MODE, SQL_MODE='NO_AUTO_VALUE_ON_ZERO' */;
/*!40111 SET @OLD_SQL_NOTES=@@SQL_NOTES, SQL_NOTES=0 */;

--
-- Table structure for table `auth_tokens`
--

DROP TABLE IF EXISTS `auth_tokens`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `auth_tokens` (
  `token` varchar(64) NOT NULL,
  `user_id` varchar(36) NOT NULL,
  `created_at` bigint NOT NULL,
  PRIMARY KEY (`token`),
  KEY `idx_tokens_user` (`user_id`),
  CONSTRAINT `fk_tokens_user` FOREIGN KEY (`user_id`) REFERENCES `users` (`id`) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `auth_tokens`
--

LOCK TABLES `auth_tokens` WRITE;
/*!40000 ALTER TABLE `auth_tokens` DISABLE KEYS */;
INSERT INTO `auth_tokens` VALUES ('4fd87842-fd45-409b-beb7-1d5a453df5d0','af5f7756-a1d5-4e48-ba42-bf1759c74b84',1782927159329),('8e26410c-1ed6-497f-a8a1-81f070530417','af5f7756-a1d5-4e48-ba42-bf1759c74b84',1782894153720);
/*!40000 ALTER TABLE `auth_tokens` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Table structure for table `email_outbox`
--

DROP TABLE IF EXISTS `email_outbox`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `email_outbox` (
  `id` varchar(36) NOT NULL,
  `recipient` varchar(255) NOT NULL,
  `subject` varchar(512) NOT NULL,
  `body` mediumtext NOT NULL,
  `created_at` bigint NOT NULL,
  PRIMARY KEY (`id`),
  KEY `idx_outbox_created` (`created_at`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `email_outbox`
--

LOCK TABLES `email_outbox` WRITE;
/*!40000 ALTER TABLE `email_outbox` DISABLE KEYS */;
INSERT INTO `email_outbox` VALUES ('007cdc0f-655e-4a39-be9c-a577a411a3b0','siddhi75rop@gmail.com','SafePath AI Alert - Trip Started','SafePath AI Alert\n\nUser: siddhi\nTrip Started\n\nSource: Baya Karve Hostel Complex, Lane Number 1, Shahu Colony, Karve Nagar, Anandnagar, Pune City Subdistrict, Pune District, Maharashtra, 411052, India\nDestination: Pandharpur, Solapur District, Maharashtra, 413300, India\n\nLive Tracking Link:\nhttp://localhost:8080/guardian.html?sessionId=f9e4a243-871c-482d-b9b9-2051931330da&key=4031ba79-7ae7-4c06-86b7-20f0b92c44ca',1782926255604),('0aba7619-f2bc-47f8-ae0d-953b8e89f626','siddhi75rop@gmail.com','SafePath AI - High Risk Alert','High Risk Alert\n\nUser: siddhi\n\nCurrent Location:\n17.6849975, 75.305603\nhttps://www.google.com/maps?q=17.6849975,75.305603\n\nSafety Score: 29/100\n\nPlease monitor the user\'s journey immediately.\nLive Tracking Link:\nhttp://localhost:8080/guardian.html?sessionId=f9e4a243-871c-482d-b9b9-2051931330da&key=4031ba79-7ae7-4c06-86b7-20f0b92c44ca',1782926295799),('0ce3aa00-1a07-4523-8419-6107eb7d2d3a','siddhi75rop@gmail.com','SafePath AI - Emergency SOS from siddhi','EMERGENCY SOS ALERT\n\nUser: siddhi\n\nAuto emergency: no movement detected for 90 seconds. Location: 17.68500, 75.30560. Safety: 29/100 (RISKY). Live map: http://localhost:8080/guardian.html?sessionId=c67fae62-6cea-4c8e-8ee5-cfd5a542ee0b&key=14a6618a-fba6-4bb8-90cd-081a7d533396\n\nCurrent Location:\n17.6849975, 75.305603\nhttps://www.google.com/maps?q=17.6849975,75.305603\n\nSafety Score: 29/100\n\nPlease contact the user immediately.\nLive tracking:\nhttp://localhost:8080/guardian.html?sessionId=c67fae62-6cea-4c8e-8ee5-cfd5a542ee0b&key=14a6618a-fba6-4bb8-90cd-081a7d533396',1782927329895),('22f1bd3c-e819-4bcf-9feb-28ec595d42e4','siddhi75rop@gmail.com','SafePath AI - Emergency SOS from siddhi','EMERGENCY SOS ALERT\n\nUser: siddhi\n\nEmergency SOS. Location: 28.61290, 77.22950. Safety: 29/100 (RISKY). Live map: http://localhost:8080/guardian.html?sessionId=0cff9f5c-3093-486a-adac-a433b870ddda&key=f30156a8-e677-4bc9-a9ec-bf4f5834bad3\n\nCurrent Location:\n28.6129, 77.2295\nhttps://www.google.com/maps?q=28.6129,77.2295\n\nSafety Score: 29/100\n\nPlease contact the user immediately.\nLive tracking:\nhttp://localhost:8080/guardian.html?sessionId=0cff9f5c-3093-486a-adac-a433b870ddda&key=f30156a8-e677-4bc9-a9ec-bf4f5834bad3',1782932635299),('236fea94-b896-4dda-afd4-3a8bd8f5043d','siddhi75rop@gmail.com','SafePath AI - Emergency SOS from siddhi','EMERGENCY SOS ALERT\n\nUser: siddhi\n\nEmergency SOS. Location: location pending. Safety: 72/100 (SAFE). Live map: http://localhost:8080/guardian.html?sessionId=f9e4a243-871c-482d-b9b9-2051931330da&key=4031ba79-7ae7-4c06-86b7-20f0b92c44ca\n\nCurrent Location:\n0.0, 0.0\nhttps://www.google.com/maps?q=0.0,0.0\n\nSafety Score: 72/100\n\nPlease contact the user immediately.\nLive tracking:\n',1782923406278),('254d9ffb-9202-4c0d-8823-7e84da8d1e74','siddhi75rop@gmail.com','SafePath AI - Emergency SOS from siddhi','EMERGENCY SOS ALERT\n\nUser: siddhi\n\nEmergency SOS. Location: 17.68500, 75.30560. Safety: 29/100 (RISKY). Live map: http://localhost:8080/guardian.html?sessionId=c67fae62-6cea-4c8e-8ee5-cfd5a542ee0b&key=14a6618a-fba6-4bb8-90cd-081a7d533396\n\nCurrent Location:\n17.6849975, 75.305603\nhttps://www.google.com/maps?q=17.6849975,75.305603\n\nSafety Score: 29/100\n\nPlease contact the user immediately.\nLive tracking:\nhttp://localhost:8080/guardian.html?sessionId=c67fae62-6cea-4c8e-8ee5-cfd5a542ee0b&key=14a6618a-fba6-4bb8-90cd-081a7d533396',1782928005958),('2a0ffa27-9fba-4168-bbd5-a77f66f60965','siddhi75rop@gmail.com','SafePath AI Alert - Trip Started','SafePath AI Alert\n\nUser: siddhi\nTrip Started\n\nSource: Unknown\nDestination: Unknown\n\nLive Tracking Link:\nhttp://localhost:8080/guardian.html?sessionId=fe615251-7dfe-47c0-9fa4-f5f8a3607d7d&key=a2a8ae86-2ae1-4aa8-8269-f1f69689add0',1782930291240),('2ac0f08b-65e9-49fc-be0a-a09a99f46b85','siddhi75rop@gmail.com','SafePath AI Alert - Trip Started','SafePath AI Alert\n\nUser: siddhi\nTrip Started\n\nSource: Unknown\nDestination: Unknown\n\nLive Tracking Link:\nhttp://localhost:8080/guardian.html?sessionId=07a40e82-d40f-4b2c-93c5-7ad05f577adf&key=35031d8d-44ca-4410-8192-eaf9c2a10013',1782928050158),('2b61590d-c25f-40ae-aeac-ae37bc574913','siddhi75rop@gmail.com','SafePath AI - High Risk Alert','High Risk Alert\n\nUser: siddhi\n\nCurrent Location:\n17.6849975, 75.305603\nhttps://www.google.com/maps?q=17.6849975,75.305603\n\nSafety Score: 29/100\n\nPlease monitor the user\'s journey immediately.\nLive Tracking Link:\nhttp://localhost:8080/guardian.html?sessionId=d388eb89-bfbb-4f63-aac2-0f2a5f53003c&key=3ff6d208-04f3-432f-82ea-f65ef766461e',1782928925000),('3115c218-93d6-4914-8333-920742c2a4ad','siddhi75rop@gmail.com','SafePath AI - High Risk Alert','High Risk Alert\n\nUser: siddhi\n\nCurrent Location:\n17.6849975, 75.305603\nhttps://www.google.com/maps?q=17.6849975,75.305603\n\nSafety Score: 29/100\n\nPlease monitor the user\'s journey immediately.\nLive Tracking Link:\n',1782927187769),('319b071b-fcfe-4c16-bf73-a1a9c3bb93b9','siddhi75rop@gmail.com','SafePath AI - High Risk Alert','High Risk Alert\n\nUser: siddhi\n\nCurrent Location:\n17.6849975, 75.305603\nhttps://www.google.com/maps?q=17.6849975,75.305603\n\nSafety Score: 29/100\n\nPlease monitor the user\'s journey immediately.\nLive Tracking Link:\n',1782929057769),('3682f79a-c135-4355-958e-6e97f35ad7da','siddhi75rop@gmail.com','SafePath AI Alert - Trip Started','SafePath AI Alert\n\nUser: siddhi\nTrip Started\n\nSource: Connaught Place, Chanakya Puri Tehsil, New Delhi, Delhi, 110001, India\nDestination: India Gate, Shahjahan Road, Pandara Park, Chanakya Puri Tehsil, New Delhi, Delhi, 020626, India\n\nLive Tracking Link:\nhttp://localhost:8080/guardian.html?sessionId=fda72082-a189-4f2b-99e8-327f3b770409&key=253a48da-bd35-47ab-8e40-dd698d3fe3b9',1782932180722),('36b09dd8-db21-459a-b2f7-2a0087654e40','siddhi75rop@gmail.com','SafePath AI - Emergency SOS from siddhi','EMERGENCY SOS ALERT\n\nUser: siddhi\n\nAuto emergency: no movement detected for 90 seconds. Location: 17.68500, 75.30560. Safety: 29/100 (RISKY). Live map: http://localhost:8080/guardian.html?sessionId=1cdd55b8-71ed-457c-b182-1ea7c964adc2&key=88bed6c1-8868-48c7-a8d8-ba039a43d76c\n\nCurrent Location:\n17.6849975, 75.305603\nhttps://www.google.com/maps?q=17.6849975,75.305603\n\nSafety Score: 29/100\n\nPlease contact the user immediately.\nLive tracking:\nhttp://localhost:8080/guardian.html?sessionId=1cdd55b8-71ed-457c-b182-1ea7c964adc2&key=88bed6c1-8868-48c7-a8d8-ba039a43d76c',1782931335474),('3986817a-6ae9-4115-800b-f2576738cd40','siddhi75rop@gmail.com','SafePath AI - Emergency SOS from siddhi','EMERGENCY SOS ALERT\n\nUser: siddhi\n\nEmergency SOS. Location: 17.68500, 75.30560. Safety: 29/100 (RISKY). Live map: http://localhost:8080/guardian.html?sessionId=1cdd55b8-71ed-457c-b182-1ea7c964adc2&key=88bed6c1-8868-48c7-a8d8-ba039a43d76c\n\nCurrent Location:\n17.6849975, 75.305603\nhttps://www.google.com/maps?q=17.6849975,75.305603\n\nSafety Score: 29/100\n\nPlease contact the user immediately.\nLive tracking:\nhttp://localhost:8080/guardian.html?sessionId=1cdd55b8-71ed-457c-b182-1ea7c964adc2&key=88bed6c1-8868-48c7-a8d8-ba039a43d76c',1782930592614),('3a8b5685-14e6-43dc-bb4e-6880dfcb6288','siddhi75rop@gmail.com','SafePath AI - Emergency SOS from siddhi','EMERGENCY SOS ALERT\n\nUser: siddhi\n\nNo movement detected for 90 seconds\n\nCurrent Location:\n17.6849975, 75.305603\nhttps://www.google.com/maps?q=17.6849975,75.305603\n\nSafety Score: 29/100\n\nPlease contact the user immediately.\nLive tracking:\nhttp://localhost:8080/guardian.html?sessionId=1cdd55b8-71ed-457c-b182-1ea7c964adc2&key=88bed6c1-8868-48c7-a8d8-ba039a43d76c',1782931328617),('3f588b76-a2f8-4c74-a910-528c86eec7b0','siddhi75rop@gmail.com','SafePath AI - High Risk Alert','High Risk Alert\n\nUser: siddhi\n\nCurrent Location:\n17.6849975, 75.305603\nhttps://www.google.com/maps?q=17.6849975,75.305603\n\nSafety Score: 29/100\n\nPlease monitor the user\'s journey immediately.\nLive Tracking Link:\nhttp://localhost:8080/guardian.html?sessionId=c67fae62-6cea-4c8e-8ee5-cfd5a542ee0b&key=14a6618a-fba6-4bb8-90cd-081a7d533396',1782927499833),('497d33fe-abf9-40b3-97dc-c2e8f6e12214','siddhi75rop@gmail.com','SafePath AI - Emergency SOS from siddhi','EMERGENCY SOS ALERT\n\nUser: siddhi\n\nAuto emergency: no movement detected for 90 seconds. Location: 17.68500, 75.30560. Safety: 29/100 (RISKY). Live map: http://localhost:8080/guardian.html?sessionId=f9e4a243-871c-482d-b9b9-2051931330da&key=4031ba79-7ae7-4c06-86b7-20f0b92c44ca\n\nCurrent Location:\n17.6849975, 75.305603\nhttps://www.google.com/maps?q=17.6849975,75.305603\n\nSafety Score: 29/100\n\nPlease contact the user immediately.\nLive tracking:\nhttp://localhost:8080/guardian.html?sessionId=f9e4a243-871c-482d-b9b9-2051931330da&key=4031ba79-7ae7-4c06-86b7-20f0b92c44ca',1782926697441),('4d93f2ae-e5f8-4c4b-bb4e-4f71fdb69ddc','siddhi75rop@gmail.com','SafePath AI - Emergency SOS from siddhi','EMERGENCY SOS ALERT\n\nUser: siddhi\n\nNo movement detected for 90 seconds\n\nCurrent Location:\n17.6849975, 75.305603\nhttps://www.google.com/maps?q=17.6849975,75.305603\n\nSafety Score: 29/100\n\nPlease contact the user immediately.\nLive tracking:\nhttp://localhost:8080/guardian.html?sessionId=1cdd55b8-71ed-457c-b182-1ea7c964adc2&key=88bed6c1-8868-48c7-a8d8-ba039a43d76c',1782930760726),('51cf80b7-2739-4d3b-90a7-c8c0df24a689','siddhi75rop@gmail.com','SafePath AI - High Risk Alert','High Risk Alert\n\nUser: siddhi\n\nCurrent Location:\n17.6849975, 75.305603\nhttps://www.google.com/maps?q=17.6849975,75.305603\n\nSafety Score: 29/100\n\nPlease monitor the user\'s journey immediately.\nLive Tracking Link:\n',1782926829450),('53ee1acf-f375-4667-9cb9-1b1a1b09565b','siddhi75rop@gmail.com','SafePath AI - High Risk Alert','High Risk Alert\n\nUser: siddhi\n\nCurrent Location:\n17.6849975, 75.305603\nhttps://www.google.com/maps?q=17.6849975,75.305603\n\nSafety Score: 29/100\n\nPlease monitor the user\'s journey immediately.\nLive Tracking Link:\nhttp://localhost:8080/guardian.html?sessionId=f9e4a243-871c-482d-b9b9-2051931330da&key=4031ba79-7ae7-4c06-86b7-20f0b92c44ca',1782926686420),('5b19868f-9753-4f29-ac02-40aa93d27c35','siddhi75rop@gmail.com','SafePath AI - High Risk Alert','High Risk Alert\n\nUser: siddhi\n\nCurrent Location:\n28.6315, 77.2167\nhttps://www.google.com/maps?q=28.6315,77.2167\n\nSafety Score: 29/100\n\nPlease monitor the user\'s journey immediately.\nLive Tracking Link:\nhttp://localhost:8080/guardian.html?sessionId=0cff9f5c-3093-486a-adac-a433b870ddda&key=f30156a8-e677-4bc9-a9ec-bf4f5834bad3',1782932569559),('5b88d8f4-63b9-484b-b5f2-d0972cb37f0b','siddhi75rop@gmail.com','SafePath AI - High Risk Alert','High Risk Alert\n\nUser: siddhi\n\nCurrent Location:\n28.6315, 77.2167\nhttps://www.google.com/maps?q=28.6315,77.2167\n\nSafety Score: 29/100\n\nPlease monitor the user\'s journey immediately.\nLive Tracking Link:\nhttp://localhost:8080/guardian.html?sessionId=a634cc1c-26f5-4599-ac71-f30d3a2bd985&key=dfa560b9-f313-4643-8da8-addd1b90676b',1782931902272),('5be45981-65fd-47f7-b00a-ddd2a6dc7aca','siddhi75rop@gmail.com','SafePath AI - Emergency SOS from siddhi','EMERGENCY SOS ALERT\n\nUser: siddhi\n\nAuto emergency: no movement detected for 90 seconds. Location: 17.68500, 75.30560. Safety: 29/100 (RISKY). Live map: http://localhost:8080/guardian.html?sessionId=1cdd55b8-71ed-457c-b182-1ea7c964adc2&key=88bed6c1-8868-48c7-a8d8-ba039a43d76c\n\nCurrent Location:\n17.6849975, 75.305603\nhttps://www.google.com/maps?q=17.6849975,75.305603\n\nSafety Score: 29/100\n\nPlease contact the user immediately.\nLive tracking:\nhttp://localhost:8080/guardian.html?sessionId=1cdd55b8-71ed-457c-b182-1ea7c964adc2&key=88bed6c1-8868-48c7-a8d8-ba039a43d76c',1782931506021),('5bef08fa-f792-4038-9d79-ede1c3f858ed','siddhi75rop@gmail.com','SafePath AI - Emergency SOS from siddhi','EMERGENCY SOS ALERT\n\nUser: siddhi\n\nNo movement detected for 90 seconds\n\nCurrent Location:\n17.6849975, 75.305603\nhttps://www.google.com/maps?q=17.6849975,75.305603\n\nSafety Score: 29/100\n\nPlease contact the user immediately.\nLive tracking:\nhttp://localhost:8080/guardian.html?sessionId=f9e4a243-871c-482d-b9b9-2051931330da&key=4031ba79-7ae7-4c06-86b7-20f0b92c44ca',1782926691707),('62717d3c-b7b6-432a-9dcb-19676bdc8fe3','siddhi75rop@gmail.com','SafePath AI - High Risk Alert','High Risk Alert\n\nUser: siddhi\n\nCurrent Location:\n17.6849975, 75.305603\nhttps://www.google.com/maps?q=17.6849975,75.305603\n\nSafety Score: 29/100\n\nPlease monitor the user\'s journey immediately.\nLive Tracking Link:\nhttp://localhost:8080/guardian.html?sessionId=1cdd55b8-71ed-457c-b182-1ea7c964adc2&key=88bed6c1-8868-48c7-a8d8-ba039a43d76c',1782930823427),('63c83126-d29c-4fe1-b689-998d19be1f9b','siddhi75rop@gmail.com','SafePath AI - Emergency SOS from siddhi','EMERGENCY SOS ALERT\n\nUser: siddhi\n\nAuto emergency: no movement detected for 90 seconds. Location: 17.68500, 75.30560. Safety: 29/100 (Low). Live map: http://localhost:8080/guardian.html?sessionId=d388eb89-bfbb-4f63-aac2-0f2a5f53003c&key=3ff6d208-04f3-432f-82ea-f65ef766461e\n\nCurrent Location:\n17.6849975, 75.305603\nhttps://www.google.com/maps?q=17.6849975,75.305603\n\nSafety Score: 29/100\n\nPlease contact the user immediately.\nLive tracking:\nhttp://localhost:8080/guardian.html?sessionId=d388eb89-bfbb-4f63-aac2-0f2a5f53003c&key=3ff6d208-04f3-432f-82ea-f65ef766461e',1782928938213),('6648d48f-ac88-4287-8a01-c3afa5be96a0','siddhi75rop@gmail.com','SafePath AI Alert - Trip Started','SafePath AI Alert\n\nUser: siddhi\nTrip Started\n\nSource: Connaught Place, Chanakya Puri Tehsil, New Delhi, Delhi, 110001, India\nDestination: India Gate, Shahjahan Road, Pandara Park, Chanakya Puri Tehsil, New Delhi, Delhi, 020626, India\n\nLive Tracking Link:\nhttp://localhost:8080/guardian.html?sessionId=a448e748-d365-4af7-aa97-e0a0171d12fd&key=e19daa13-cac7-4622-aa6f-68860ddb2dd5',1782926875766),('6cad090f-0d03-4d3a-879c-edfda391023f','siddhi75rop@gmail.com','SafePath AI Alert - Trip Started','SafePath AI Alert\n\nUser: siddhi\nTrip Started\n\nSource: Unknown\nDestination: Unknown\n\nLive Tracking Link:\nhttp://localhost:8080/guardian.html?sessionId=07a40e82-d40f-4b2c-93c5-7ad05f577adf&key=35031d8d-44ca-4410-8192-eaf9c2a10013',1782928099398),('71834ceb-49a1-434a-9f7a-25449b33a35e','siddhi75rop@gmail.com','SafePath AI Alert - Trip Started','SafePath AI Alert\n\nUser: siddhi\nTrip Started\n\nSource: Unknown\nDestination: Unknown\n\nLive Tracking Link:\nhttp://localhost:8080/guardian.html?sessionId=c67fae62-6cea-4c8e-8ee5-cfd5a542ee0b&key=14a6618a-fba6-4bb8-90cd-081a7d533396',1782927194374),('72c4fe46-0817-498f-b2c2-24db0d07efcd','siddhi75rop@gmail.com','SafePath AI - Emergency SOS from siddhi','EMERGENCY SOS ALERT\n\nUser: siddhi\n\nNo movement detected for 90 seconds\n\nCurrent Location:\n17.6849975, 75.305603\nhttps://www.google.com/maps?q=17.6849975,75.305603\n\nSafety Score: 29/100\n\nPlease contact the user immediately.\nLive tracking:\nhttp://localhost:8080/guardian.html?sessionId=d388eb89-bfbb-4f63-aac2-0f2a5f53003c&key=3ff6d208-04f3-432f-82ea-f65ef766461e',1782929041096),('742bbbe0-9da4-4303-9240-5bb90873df82','siddhi75rop@gmail.com','SafePath AI Alert - Trip Started','SafePath AI Alert\n\nUser: siddhi\nTrip Started\n\nSource: Unknown\nDestination: Unknown\n\nLive Tracking Link:\nhttp://localhost:8080/guardian.html?sessionId=c67fae62-6cea-4c8e-8ee5-cfd5a542ee0b&key=14a6618a-fba6-4bb8-90cd-081a7d533396',1782927210958),('7668960a-2d3f-43c6-9e95-9aca7877d197','siddhi75rop@gmail.com','SafePath AI - Emergency SOS from siddhi','EMERGENCY SOS ALERT\n\nUser: siddhi\n\nNo movement detected for 90 seconds\n\nCurrent Location:\n17.6849975, 75.305603\nhttps://www.google.com/maps?q=17.6849975,75.305603\n\nSafety Score: 29/100\n\nPlease contact the user immediately.\nLive tracking:\n',1782929195262),('77d1d08f-b7d9-48b9-8a7e-37ccf9e9a6ee','siddhi75rop@gmail.com','SafePath AI - High Risk Alert','High Risk Alert\n\nUser: siddhi\n\nCurrent Location:\n28.6315, 77.2167\nhttps://www.google.com/maps?q=28.6315,77.2167\n\nSafety Score: 29/100\n\nPlease monitor the user\'s journey immediately.\nLive Tracking Link:\nhttp://localhost:8080/guardian.html?sessionId=d388eb89-bfbb-4f63-aac2-0f2a5f53003c&key=3ff6d208-04f3-432f-82ea-f65ef766461e',1782928214609),('7c477259-ce67-4edb-b8e2-4469356ab085','siddhi75rop@gmail.com','SafePath AI Alert - Trip Started','SafePath AI Alert\n\nUser: siddhi\nTrip Started\n\nSource: Unknown\nDestination: Unknown\n\nLive Tracking Link:\nhttp://localhost:8080/guardian.html?sessionId=07a40e82-d40f-4b2c-93c5-7ad05f577adf&key=35031d8d-44ca-4410-8192-eaf9c2a10013',1782928105191),('7e5eea44-9f36-46c3-99ce-8a8db899a3b2','siddhi75rop@gmail.com','SafePath AI - Emergency SOS from siddhi','EMERGENCY SOS ALERT\n\nUser: siddhi\n\nAuto emergency: no movement detected for 90 seconds. Location: 17.68500, 75.30560. Safety: 29/100 (Low). Live map: http://localhost:8080/guardian.html?sessionId=a6db8b81-d06e-4bac-99ba-4429f16d9541&key=9c872697-2541-4e9e-ba68-b67dc7b132d2\n\nCurrent Location:\n17.6849975, 75.305603\nhttps://www.google.com/maps?q=17.6849975,75.305603\n\nSafety Score: 29/100\n\nPlease contact the user immediately.\nLive tracking:\n',1782929201779),('7f83a337-874d-45ae-8e13-396ebb5f13d6','siddhi75rop@gmail.com','SafePath AI Alert - Trip Started','SafePath AI Alert\n\nUser: siddhi\nTrip Started\n\nSource: Unknown\nDestination: Unknown\n\nLive Tracking Link:\nhttp://localhost:8080/guardian.html?sessionId=c67fae62-6cea-4c8e-8ee5-cfd5a542ee0b&key=14a6618a-fba6-4bb8-90cd-081a7d533396',1782927216701),('802f7171-9317-4774-89eb-1523f261cf46','siddhi75rop@gmail.com','SafePath AI - Emergency SOS from siddhi','EMERGENCY SOS ALERT\n\nUser: siddhi\n\nNo movement detected for 90 seconds\n\nCurrent Location:\n17.6849975, 75.305603\nhttps://www.google.com/maps?q=17.6849975,75.305603\n\nSafety Score: 29/100\n\nPlease contact the user immediately.\nLive tracking:\nhttp://localhost:8080/guardian.html?sessionId=c67fae62-6cea-4c8e-8ee5-cfd5a542ee0b&key=14a6618a-fba6-4bb8-90cd-081a7d533396',1782927793103),('823bba58-e807-4b5e-a025-7f011ffabbc8','siddhi75rop@gmail.com','SafePath AI - Emergency SOS from siddhi','EMERGENCY SOS ALERT\n\nUser: siddhi\n\nNo movement detected for 90 seconds\n\nCurrent Location:\n17.6849975, 75.305603\nhttps://www.google.com/maps?q=17.6849975,75.305603\n\nSafety Score: 29/100\n\nPlease contact the user immediately.\nLive tracking:\nhttp://localhost:8080/guardian.html?sessionId=f9e4a243-871c-482d-b9b9-2051931330da&key=4031ba79-7ae7-4c06-86b7-20f0b92c44ca',1782926590697),('82c5e6f4-a80b-47a7-9c41-8604a77cc487','siddhi75rop@gmail.com','SafePath AI - High Risk Alert','High Risk Alert\n\nUser: siddhi\n\nCurrent Location:\n28.6315, 77.2167\nhttps://www.google.com/maps?q=28.6315,77.2167\n\nSafety Score: 29/100\n\nPlease monitor the user\'s journey immediately.\nLive Tracking Link:\nhttp://localhost:8080/guardian.html?sessionId=556e3afe-5386-4be1-8acf-199bd7b8198d&key=308e0cee-0f47-42f0-adcd-5e2507c1bda3',1782932340505),('86c8249e-ba77-4464-8697-8e05bf81bae7','siddhi75rop@gmail.com','SafePath AI Alert - Trip Started','SafePath AI Alert\n\nUser: siddhi\nTrip Started\n\nSource: Connaught Place, Chanakya Puri Tehsil, New Delhi, Delhi, 110001, India\nDestination: India Gate, Shahjahan Road, Pandara Park, Chanakya Puri Tehsil, New Delhi, Delhi, 020626, India\n\nLive Tracking Link:\nhttp://localhost:8080/guardian.html?sessionId=1cdd55b8-71ed-457c-b182-1ea7c964adc2&key=88bed6c1-8868-48c7-a8d8-ba039a43d76c',1782930472790),('8cc34d25-d238-44f7-bee2-f38ee20906b0','siddhi75rop@gmail.com','SafePath AI - Emergency SOS from siddhi','EMERGENCY SOS ALERT\n\nUser: siddhi\n\nNo movement detected for 90 seconds\n\nCurrent Location:\n17.6849975, 75.305603\nhttps://www.google.com/maps?q=17.6849975,75.305603\n\nSafety Score: 29/100\n\nPlease contact the user immediately.\nLive tracking:\nhttp://localhost:8080/guardian.html?sessionId=d388eb89-bfbb-4f63-aac2-0f2a5f53003c&key=3ff6d208-04f3-432f-82ea-f65ef766461e',1782928931265),('8f5db4af-8a0c-4f2c-a1d3-07d3f2873710','siddhi75rop@gmail.com','SafePath AI - Emergency SOS from siddhi','EMERGENCY SOS ALERT\n\nUser: siddhi\n\nAuto emergency: no movement detected for 90 seconds. Location: 17.68500, 75.30560. Safety: 29/100 (RISKY). Live map: http://localhost:8080/guardian.html?sessionId=1cdd55b8-71ed-457c-b182-1ea7c964adc2&key=88bed6c1-8868-48c7-a8d8-ba039a43d76c\n\nCurrent Location:\n17.6849975, 75.305603\nhttps://www.google.com/maps?q=17.6849975,75.305603\n\nSafety Score: 29/100\n\nPlease contact the user immediately.\nLive tracking:\nhttp://localhost:8080/guardian.html?sessionId=1cdd55b8-71ed-457c-b182-1ea7c964adc2&key=88bed6c1-8868-48c7-a8d8-ba039a43d76c',1782930766477),('91751a1a-13ac-4799-afef-b263a46548e2','siddhi75rop@gmail.com','SafePath AI - Emergency SOS from siddhi','EMERGENCY SOS ALERT\n\nUser: siddhi\n\nAuto emergency: no movement detected for 90 seconds. Location: 17.68500, 75.30560. Safety: 29/100 (RISKY). Live map: http://localhost:8080/guardian.html?sessionId=f9e4a243-871c-482d-b9b9-2051931330da&key=4031ba79-7ae7-4c06-86b7-20f0b92c44ca\n\nCurrent Location:\n17.6849975, 75.305603\nhttps://www.google.com/maps?q=17.6849975,75.305603\n\nSafety Score: 29/100\n\nPlease contact the user immediately.\nLive tracking:\nhttp://localhost:8080/guardian.html?sessionId=f9e4a243-871c-482d-b9b9-2051931330da&key=4031ba79-7ae7-4c06-86b7-20f0b92c44ca',1782926596413),('93a5bd26-84b2-41ea-965f-9c62ca45e646','siddhi75rop@gmail.com','SafePath AI - Emergency SOS from siddhi','EMERGENCY SOS ALERT\n\nUser: siddhi\n\nNo movement detected for 90 seconds\n\nCurrent Location:\n17.6849975, 75.305603\nhttps://www.google.com/maps?q=17.6849975,75.305603\n\nSafety Score: 29/100\n\nPlease contact the user immediately.\nLive tracking:\nhttp://localhost:8080/guardian.html?sessionId=c67fae62-6cea-4c8e-8ee5-cfd5a542ee0b&key=14a6618a-fba6-4bb8-90cd-081a7d533396',1782927634945),('a40a4618-3f6f-4088-ad7d-871ec9ebf11e','siddhi75rop@gmail.com','SafePath AI Alert - Trip Started','SafePath AI Alert\n\nUser: siddhi\nTrip Started\n\nSource: Connaught Place, Chanakya Puri Tehsil, New Delhi, Delhi, 110001, India\nDestination: India Gate, Shahjahan Road, Pandara Park, Chanakya Puri Tehsil, New Delhi, Delhi, 020626, India\n\nLive Tracking Link:\nhttp://localhost:8080/guardian.html?sessionId=d388eb89-bfbb-4f63-aac2-0f2a5f53003c&key=3ff6d208-04f3-432f-82ea-f65ef766461e',1782928206755),('a711592e-6299-4bc9-a881-7196c3b44008','siddhi75rop@gmail.com','SafePath AI - High Risk Alert','High Risk Alert\n\nUser: siddhi\n\nCurrent Location:\n17.6849975, 75.305603\nhttps://www.google.com/maps?q=17.6849975,75.305603\n\nSafety Score: 29/100\n\nPlease monitor the user\'s journey immediately.\nLive Tracking Link:\nhttp://localhost:8080/guardian.html?sessionId=fe615251-7dfe-47c0-9fa4-f5f8a3607d7d&key=a2a8ae86-2ae1-4aa8-8269-f1f69689add0',1782930308890),('aa678082-56dd-4348-a7f7-f04d54e73cd5','siddhi75rop@gmail.com','SafePath AI Alert - Trip Started','SafePath AI Alert\n\nUser: siddhi\nTrip Started\n\nSource: Unknown\nDestination: Unknown\n\nLive Tracking Link:\nhttp://localhost:8080/guardian.html?sessionId=fe615251-7dfe-47c0-9fa4-f5f8a3607d7d&key=a2a8ae86-2ae1-4aa8-8269-f1f69689add0',1782930303332),('ab29410f-b8b9-41f7-a839-eadc13636829','siddhi75rop@gmail.com','SafePath AI - Emergency SOS from siddhi','EMERGENCY SOS ALERT\n\nUser: siddhi\n\nAuto emergency: no movement detected for 90 seconds. Location: 17.68500, 75.30560. Safety: 29/100 (RISKY). Live map: http://localhost:8080/guardian.html?sessionId=c67fae62-6cea-4c8e-8ee5-cfd5a542ee0b&key=14a6618a-fba6-4bb8-90cd-081a7d533396\n\nCurrent Location:\n17.6849975, 75.305603\nhttps://www.google.com/maps?q=17.6849975,75.305603\n\nSafety Score: 29/100\n\nPlease contact the user immediately.\nLive tracking:\nhttp://localhost:8080/guardian.html?sessionId=c67fae62-6cea-4c8e-8ee5-cfd5a542ee0b&key=14a6618a-fba6-4bb8-90cd-081a7d533396',1782927628847),('b3328ad0-b966-465e-803b-55514052fe16','siddhi75rop@gmail.com','SafePath AI - High Risk Alert','High Risk Alert\n\nUser: siddhi\n\nCurrent Location:\n28.6315, 77.2167\nhttps://www.google.com/maps?q=28.6315,77.2167\n\nSafety Score: 29/100\n\nPlease monitor the user\'s journey immediately.\nLive Tracking Link:\nhttp://localhost:8080/guardian.html?sessionId=1cdd55b8-71ed-457c-b182-1ea7c964adc2&key=88bed6c1-8868-48c7-a8d8-ba039a43d76c',1782930477958),('b7276a6d-0217-4f43-bd1e-08f2f0417929','siddhi75rop@gmail.com','SafePath AI - Emergency SOS from siddhi','EMERGENCY SOS ALERT\n\nUser: siddhi\n\nNo movement detected for 90 seconds\n\nCurrent Location:\n17.6849975, 75.305603\nhttps://www.google.com/maps?q=17.6849975,75.305603\n\nSafety Score: 29/100\n\nPlease contact the user immediately.\nLive tracking:\nhttp://localhost:8080/guardian.html?sessionId=c67fae62-6cea-4c8e-8ee5-cfd5a542ee0b&key=14a6618a-fba6-4bb8-90cd-081a7d533396',1782927336209),('b7752e01-2dac-4f31-953e-bfaa21294cd6','siddhi75rop@gmail.com','SafePath AI - Emergency SOS from siddhi','EMERGENCY SOS ALERT\n\nUser: siddhi\n\nAuto emergency: no movement detected for 90 seconds. Location: 17.68500, 75.30560. Safety: 29/100 (RISKY). Live map: http://localhost:8080/guardian.html?sessionId=c67fae62-6cea-4c8e-8ee5-cfd5a542ee0b&key=14a6618a-fba6-4bb8-90cd-081a7d533396\n\nCurrent Location:\n17.6849975, 75.305603\nhttps://www.google.com/maps?q=17.6849975,75.305603\n\nSafety Score: 29/100\n\nPlease contact the user immediately.\nLive tracking:\nhttp://localhost:8080/guardian.html?sessionId=c67fae62-6cea-4c8e-8ee5-cfd5a542ee0b&key=14a6618a-fba6-4bb8-90cd-081a7d533396',1782927785481),('c10b7f34-bc0e-4756-ad96-5863887adf7a','siddhi75rop@gmail.com','SafePath AI - High Risk Alert','High Risk Alert\n\nUser: siddhi\n\nCurrent Location:\n17.6849975, 75.305603\nhttps://www.google.com/maps?q=17.6849975,75.305603\n\nSafety Score: 29/100\n\nPlease monitor the user\'s journey immediately.\nLive Tracking Link:\n',1782928044977),('c1e974e7-00ae-40ee-9c5d-e7266b5b399f','siddhi75rop@gmail.com','SafePath AI Alert - Trip Started','SafePath AI Alert\n\nUser: siddhi\nTrip Started\n\nSource: Connaught Place, Chanakya Puri Tehsil, New Delhi, Delhi, 110001, India\nDestination: India Gate, Shahjahan Road, Pandara Park, Chanakya Puri Tehsil, New Delhi, Delhi, 020626, India\n\nLive Tracking Link:\nhttp://localhost:8080/guardian.html?sessionId=a634cc1c-26f5-4599-ac71-f30d3a2bd985&key=dfa560b9-f313-4643-8da8-addd1b90676b',1782931896705),('c374c395-7af7-43e4-bcab-3ba31146060b','siddhi75rop@gmail.com','SafePath AI Alert - Trip Started','SafePath AI Alert\n\nUser: siddhi\nTrip Started\n\nSource: Connaught Place, Chanakya Puri Tehsil, New Delhi, Delhi, 110001, India\nDestination: India Gate, Shahjahan Road, Pandara Park, Chanakya Puri Tehsil, New Delhi, Delhi, 020626, India\n\nLive Tracking Link:\nhttp://localhost:8080/guardian.html?sessionId=0cff9f5c-3093-486a-adac-a433b870ddda&key=f30156a8-e677-4bc9-a9ec-bf4f5834bad3',1782932563054),('c8515384-4e0f-4a13-b6ef-49b01892bd00','siddhi75rop@gmail.com','SafePath AI Alert - Trip Started','SafePath AI Alert\n\nUser: siddhi\nTrip Started\n\nSource: Unknown\nDestination: Unknown\n\nLive Tracking Link:\nhttp://localhost:8080/guardian.html?sessionId=fe615251-7dfe-47c0-9fa4-f5f8a3607d7d&key=a2a8ae86-2ae1-4aa8-8269-f1f69689add0',1782930297727),('ca7dcfbe-4edb-4f96-95e4-03c7468ef169','siddhi75rop@gmail.com','SafePath AI - High Risk Alert','High Risk Alert\n\nUser: siddhi\n\nCurrent Location:\n17.6849975, 75.305603\nhttps://www.google.com/maps?q=17.6849975,75.305603\n\nSafety Score: 29/100\n\nPlease monitor the user\'s journey immediately.\nLive Tracking Link:\nhttp://localhost:8080/guardian.html?sessionId=1cdd55b8-71ed-457c-b182-1ea7c964adc2&key=88bed6c1-8868-48c7-a8d8-ba039a43d76c',1782931495292),('ca8ccc39-9c19-433c-8f90-6396a4a4f2b9','siddhi75rop@gmail.com','SafePath AI - Emergency SOS from siddhi','EMERGENCY SOS ALERT\n\nUser: siddhi\n\nEmergency SOS. Location: 28.61290, 77.22950. Safety: 29/100 (RISKY). Live map: http://localhost:8080/guardian.html?sessionId=0cff9f5c-3093-486a-adac-a433b870ddda&key=f30156a8-e677-4bc9-a9ec-bf4f5834bad3\n\nCurrent Location:\n28.6129, 77.2295\nhttps://www.google.com/maps?q=28.6129,77.2295\n\nSafety Score: 29/100\n\nPlease contact the user immediately.\nLive tracking:\nhttp://localhost:8080/guardian.html?sessionId=0cff9f5c-3093-486a-adac-a433b870ddda&key=f30156a8-e677-4bc9-a9ec-bf4f5834bad3',1782932626743),('cb913d96-9cc1-4321-948b-610c9ada3570','siddhi75rop@gmail.com','SafePath AI Alert - Trip Started','SafePath AI Alert\n\nUser: siddhi\nTrip Started\n\nSource: Baya Karve Hostel Complex, Lane Number 1, Shahu Colony, Karve Nagar, Anandnagar, Pune City Subdistrict, Pune District, Maharashtra, 411052, India\nDestination: Pandharpur, Solapur District, Maharashtra, 413300, India\n\nLive Tracking Link:\nhttp://localhost:8080/guardian.html?sessionId=f9e4a243-871c-482d-b9b9-2051931330da&key=4031ba79-7ae7-4c06-86b7-20f0b92c44ca',1782926247671),('d61ed439-21b0-4e58-946e-2e62d7b18868','siddhi75rop@gmail.com','SafePath AI - Emergency SOS from siddhi','EMERGENCY SOS ALERT\n\nUser: siddhi\n\nNo movement detected for 90 seconds\n\nCurrent Location:\n17.6849975, 75.305603\nhttps://www.google.com/maps?q=17.6849975,75.305603\n\nSafety Score: 29/100\n\nPlease contact the user immediately.\nLive tracking:\nhttp://localhost:8080/guardian.html?sessionId=d388eb89-bfbb-4f63-aac2-0f2a5f53003c&key=3ff6d208-04f3-432f-82ea-f65ef766461e',1782928361756),('e6c93e6e-5426-4b8e-94d8-23827ecbb00c','siddhi75rop@gmail.com','SafePath AI - Emergency SOS from siddhi','EMERGENCY SOS ALERT\n\nUser: siddhi\n\nAuto emergency: no movement detected for 90 seconds. Location: 17.68500, 75.30560. Safety: 29/100 (Low). Live map: http://localhost:8080/guardian.html?sessionId=d388eb89-bfbb-4f63-aac2-0f2a5f53003c&key=3ff6d208-04f3-432f-82ea-f65ef766461e\n\nCurrent Location:\n17.6849975, 75.305603\nhttps://www.google.com/maps?q=17.6849975,75.305603\n\nSafety Score: 29/100\n\nPlease contact the user immediately.\nLive tracking:\nhttp://localhost:8080/guardian.html?sessionId=d388eb89-bfbb-4f63-aac2-0f2a5f53003c&key=3ff6d208-04f3-432f-82ea-f65ef766461e',1782929046987),('ea5d08e9-6dbe-4e1c-88ae-e0e97a1bc88d','siddhi75rop@gmail.com','SafePath AI - Emergency SOS from siddhi','EMERGENCY SOS ALERT\n\nUser: siddhi\n\nAuto emergency: no movement detected for 90 seconds. Location: 17.68500, 75.30560. Safety: 29/100 (Low). Live map: http://localhost:8080/guardian.html?sessionId=d388eb89-bfbb-4f63-aac2-0f2a5f53003c&key=3ff6d208-04f3-432f-82ea-f65ef766461e\n\nCurrent Location:\n17.6849975, 75.305603\nhttps://www.google.com/maps?q=17.6849975,75.305603\n\nSafety Score: 29/100\n\nPlease contact the user immediately.\nLive tracking:\nhttp://localhost:8080/guardian.html?sessionId=d388eb89-bfbb-4f63-aac2-0f2a5f53003c&key=3ff6d208-04f3-432f-82ea-f65ef766461e',1782928367668),('ebc59383-a70f-489b-b2a1-1a6a02c16737','siddhi75rop@gmail.com','SafePath AI - Emergency SOS from siddhi','EMERGENCY SOS ALERT\n\nUser: siddhi\n\nNo movement detected for 90 seconds\n\nCurrent Location:\n17.6849975, 75.305603\nhttps://www.google.com/maps?q=17.6849975,75.305603\n\nSafety Score: 29/100\n\nPlease contact the user immediately.\nLive tracking:\nhttp://localhost:8080/guardian.html?sessionId=c67fae62-6cea-4c8e-8ee5-cfd5a542ee0b&key=14a6618a-fba6-4bb8-90cd-081a7d533396',1782927442795),('f4c72e16-0d51-4bb9-8c96-da90161fd6e8','siddhi75rop@gmail.com','SafePath AI - High Risk Alert','High Risk Alert\n\nUser: siddhi\n\nCurrent Location:\n17.6849975, 75.305603\nhttps://www.google.com/maps?q=17.6849975,75.305603\n\nSafety Score: 29/100\n\nPlease monitor the user\'s journey immediately.\nLive Tracking Link:\nhttp://localhost:8080/guardian.html?sessionId=1cdd55b8-71ed-457c-b182-1ea7c964adc2&key=88bed6c1-8868-48c7-a8d8-ba039a43d76c',1782931148480),('f593de4a-54c0-47ee-8c9c-2e7eb5460a58','siddhi75rop@gmail.com','SafePath AI - Emergency SOS from siddhi','EMERGENCY SOS ALERT\n\nUser: siddhi\n\nAuto emergency: no movement detected for 90 seconds. Location: 17.68500, 75.30560. Safety: 29/100 (RISKY). Live map: http://localhost:8080/guardian.html?sessionId=c67fae62-6cea-4c8e-8ee5-cfd5a542ee0b&key=14a6618a-fba6-4bb8-90cd-081a7d533396\n\nCurrent Location:\n17.6849975, 75.305603\nhttps://www.google.com/maps?q=17.6849975,75.305603\n\nSafety Score: 29/100\n\nPlease contact the user immediately.\nLive tracking:\nhttp://localhost:8080/guardian.html?sessionId=c67fae62-6cea-4c8e-8ee5-cfd5a542ee0b&key=14a6618a-fba6-4bb8-90cd-081a7d533396',1782927449081),('f5d1d1e3-a7c1-4aaa-a301-e687e07dc9e6','siddhi75rop@gmail.com','SafePath AI Alert - Trip Started','SafePath AI Alert\n\nUser: siddhi\nTrip Started\n\nSource: Connaught Place, Chanakya Puri Tehsil, New Delhi, Delhi, 110001, India\nDestination: India Gate, Shahjahan Road, Pandara Park, Chanakya Puri Tehsil, New Delhi, Delhi, 020626, India\n\nLive Tracking Link:\nhttp://localhost:8080/guardian.html?sessionId=556e3afe-5386-4be1-8acf-199bd7b8198d&key=308e0cee-0f47-42f0-adcd-5e2507c1bda3',1782932335098),('f7ffd750-4883-453b-bab7-46151300e787','siddhi75rop@gmail.com','SafePath AI - High Risk Alert','High Risk Alert\n\nUser: siddhi\n\nCurrent Location:\n28.6315, 77.2167\nhttps://www.google.com/maps?q=28.6315,77.2167\n\nSafety Score: 29/100\n\nPlease monitor the user\'s journey immediately.\nLive Tracking Link:\nhttp://localhost:8080/guardian.html?sessionId=fda72082-a189-4f2b-99e8-327f3b770409&key=253a48da-bd35-47ab-8e40-dd698d3fe3b9',1782932186107),('f89ff0a1-16bd-435d-8377-a7edeab77805','siddhi75rop@gmail.com','SafePath AI - High Risk Alert','High Risk Alert\n\nUser: siddhi\n\nCurrent Location:\n17.6849975, 75.305603\nhttps://www.google.com/maps?q=17.6849975,75.305603\n\nSafety Score: 29/100\n\nPlease monitor the user\'s journey immediately.\nLive Tracking Link:\nhttp://localhost:8080/guardian.html?sessionId=c67fae62-6cea-4c8e-8ee5-cfd5a542ee0b&key=14a6618a-fba6-4bb8-90cd-081a7d533396',1782927801430),('fc2c9b14-094e-46c7-9a66-5137fd6ce253','siddhi75rop@gmail.com','SafePath AI - Emergency SOS from siddhi','EMERGENCY SOS ALERT\n\nUser: siddhi\n\nNo movement detected for 90 seconds\n\nCurrent Location:\n17.6849975, 75.305603\nhttps://www.google.com/maps?q=17.6849975,75.305603\n\nSafety Score: 29/100\n\nPlease contact the user immediately.\nLive tracking:\nhttp://localhost:8080/guardian.html?sessionId=1cdd55b8-71ed-457c-b182-1ea7c964adc2&key=88bed6c1-8868-48c7-a8d8-ba039a43d76c',1782931500482);
/*!40000 ALTER TABLE `email_outbox` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Table structure for table `guardians`
--

DROP TABLE IF EXISTS `guardians`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `guardians` (
  `id` varchar(36) NOT NULL,
  `user_id` varchar(36) NOT NULL,
  `name` varchar(255) NOT NULL,
  `phone` varchar(64) NOT NULL DEFAULT '',
  `email` varchar(255) NOT NULL DEFAULT '',
  `created_at` bigint NOT NULL,
  PRIMARY KEY (`id`),
  KEY `idx_guardians_user` (`user_id`),
  CONSTRAINT `fk_guardians_user` FOREIGN KEY (`user_id`) REFERENCES `users` (`id`) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `guardians`
--

LOCK TABLES `guardians` WRITE;
/*!40000 ALTER TABLE `guardians` DISABLE KEYS */;
/*!40000 ALTER TABLE `guardians` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Table structure for table `password_reset_tokens`
--

DROP TABLE IF EXISTS `password_reset_tokens`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `password_reset_tokens` (
  `token` varchar(64) NOT NULL,
  `user_id` varchar(36) NOT NULL,
  `expires_at` bigint NOT NULL,
  PRIMARY KEY (`token`),
  KEY `idx_reset_user` (`user_id`),
  CONSTRAINT `fk_reset_user` FOREIGN KEY (`user_id`) REFERENCES `users` (`id`) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `password_reset_tokens`
--

LOCK TABLES `password_reset_tokens` WRITE;
/*!40000 ALTER TABLE `password_reset_tokens` DISABLE KEYS */;
/*!40000 ALTER TABLE `password_reset_tokens` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Table structure for table `trip_history`
--

DROP TABLE IF EXISTS `trip_history`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `trip_history` (
  `id` varchar(36) NOT NULL,
  `user_id` varchar(36) NOT NULL,
  `session_id` varchar(64) DEFAULT NULL,
  `source_label` varchar(512) NOT NULL DEFAULT '',
  `dest_label` varchar(512) NOT NULL DEFAULT '',
  `distance_km` double NOT NULL DEFAULT '0',
  `safety_score` double NOT NULL DEFAULT '50',
  `route_type` varchar(32) NOT NULL DEFAULT 'balanced',
  `rating` int NOT NULL DEFAULT '0',
  `completed_at` bigint NOT NULL,
  PRIMARY KEY (`id`),
  KEY `idx_trip_history_user` (`user_id`),
  CONSTRAINT `fk_trip_history_user` FOREIGN KEY (`user_id`) REFERENCES `users` (`id`) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `trip_history`
--

LOCK TABLES `trip_history` WRITE;
/*!40000 ALTER TABLE `trip_history` DISABLE KEYS */;
INSERT INTO `trip_history` VALUES ('c2950ddd-b005-4bf6-853e-61fd163eb6d5','af5f7756-a1d5-4e48-ba42-bf1759c74b84','c65c630d-14b3-4ce2-94b1-9073c0f2f97f','Unknown','Unknown',0,42,'balanced',5,1782935577469);
/*!40000 ALTER TABLE `trip_history` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Table structure for table `unsafe_locations`
--

DROP TABLE IF EXISTS `unsafe_locations`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `unsafe_locations` (
  `id` int NOT NULL AUTO_INCREMENT,
  `latitude` double NOT NULL,
  `longitude` double NOT NULL,
  `report_count` int NOT NULL DEFAULT '1',
  `reason` varchar(255) DEFAULT NULL,
  `created_at` bigint NOT NULL,
  PRIMARY KEY (`id`),
  KEY `idx_unsafe_lat` (`latitude`),
  KEY `idx_unsafe_lng` (`longitude`)
) ENGINE=InnoDB AUTO_INCREMENT=3 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `unsafe_locations`
--

LOCK TABLES `unsafe_locations` WRITE;
/*!40000 ALTER TABLE `unsafe_locations` DISABLE KEYS */;
INSERT INTO `unsafe_locations` VALUES (1,28.6129,77.2295,1,'[Low] Deserted Street',1782934981327),(2,17.6849975,75.305603,1,'[Low] Poor Lighting',1782935507432);
/*!40000 ALTER TABLE `unsafe_locations` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Table structure for table `user_unsafe_reports`
--

DROP TABLE IF EXISTS `user_unsafe_reports`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `user_unsafe_reports` (
  `id` varchar(36) NOT NULL,
  `user_id` varchar(36) NOT NULL,
  `latitude` double NOT NULL,
  `longitude` double NOT NULL,
  `category` varchar(128) NOT NULL DEFAULT '',
  `severity` varchar(32) NOT NULL DEFAULT '',
  `description` text,
  `reason` varchar(255) NOT NULL DEFAULT '',
  `created_at` bigint NOT NULL,
  PRIMARY KEY (`id`),
  KEY `idx_user_unsafe_user` (`user_id`),
  CONSTRAINT `fk_user_unsafe_user` FOREIGN KEY (`user_id`) REFERENCES `users` (`id`) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `user_unsafe_reports`
--

LOCK TABLES `user_unsafe_reports` WRITE;
/*!40000 ALTER TABLE `user_unsafe_reports` DISABLE KEYS */;
INSERT INTO `user_unsafe_reports` VALUES ('32a60b7d-f453-4fda-b0d2-bab14f39ff58','af5f7756-a1d5-4e48-ba42-bf1759c74b84',17.6849975,75.305603,'Poor Lighting','Low','','[Low] Poor Lighting',1782935507526);
/*!40000 ALTER TABLE `user_unsafe_reports` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Table structure for table `users`
--

DROP TABLE IF EXISTS `users`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `users` (
  `id` varchar(36) NOT NULL,
  `name` varchar(255) NOT NULL,
  `email` varchar(255) NOT NULL,
  `salt` varchar(64) NOT NULL,
  `password_hash` varchar(128) NOT NULL,
  `created_at` bigint NOT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `email` (`email`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `users`
--

LOCK TABLES `users` WRITE;
/*!40000 ALTER TABLE `users` DISABLE KEYS */;
INSERT INTO `users` VALUES ('af5f7756-a1d5-4e48-ba42-bf1759c74b84','siddhi','riddhi75rop@gmail.com','a1333fe8209b36a069f37080dc5881ee','5b93b14572ab9073d7e33217c5c0822df92c312dd58b0f98808f1afaa452b033',1782894153678);
/*!40000 ALTER TABLE `users` ENABLE KEYS */;
UNLOCK TABLES;
/*!40103 SET TIME_ZONE=@OLD_TIME_ZONE */;

/*!40101 SET SQL_MODE=@OLD_SQL_MODE */;
/*!40014 SET FOREIGN_KEY_CHECKS=@OLD_FOREIGN_KEY_CHECKS */;
/*!40014 SET UNIQUE_CHECKS=@OLD_UNIQUE_CHECKS */;
/*!40101 SET CHARACTER_SET_CLIENT=@OLD_CHARACTER_SET_CLIENT */;
/*!40101 SET CHARACTER_SET_RESULTS=@OLD_CHARACTER_SET_RESULTS */;
/*!40101 SET COLLATION_CONNECTION=@OLD_COLLATION_CONNECTION */;
/*!40111 SET SQL_NOTES=@OLD_SQL_NOTES */;

-- Dump completed on 2026-07-04  0:22:49
