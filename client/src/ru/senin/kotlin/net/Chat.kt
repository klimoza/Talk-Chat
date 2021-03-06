package ru.senin.kotlin.net

import ru.senin.kotlin.net.client.ChatClient
import ru.senin.kotlin.net.client.HttpChatClient
import ru.senin.kotlin.net.client.UdpChatClient
import ru.senin.kotlin.net.client.WebSocketChatClient
import ru.senin.kotlin.net.server.ChatMessageListener
import kotlin.concurrent.thread

class Chat(
    private val name: String,
    private val registry: RegistryApi
) : ChatMessageListener {

    private var exit = false
    private var selectedUser: String? = null
    private val clients = mutableMapOf<String, ChatClient>()
    private var users = mutableMapOf<String, UserAddress>()

    private fun prompt(): String {
        val prompt = "  to [${selectedUser ?: "<not selected>"}] <<< "
        print(prompt)
        var value: String? = readLine()
        while (value.isNullOrBlank()) {
            print(prompt)
            value = readLine()
        }
        return value.trimStart()
    }

    private fun updateUsersList() {
        val registeredUsers = registry.list().execute().getOrNull()
        if (registeredUsers == null) {
            println("Cannot get users from registry")
            return
        }
        val aliveUserNames = registeredUsers.keys
        if (selectedUser != null && selectedUser !in aliveUserNames) {
            println("Selected user was removed from registry")
            selectedUser = null
        }
        users.clear()
        users.putAll(registeredUsers)
        clients.entries.retainAll { it.key in aliveUserNames }
        users.forEach { (name, address) ->
            println("$name ==> $address")
        }
    }

    private fun selectUser(userName: String) {
        val userAddress = users[userName]
        if (userAddress == null) {
            println("Unknown user '$userName'")
            return
        }
        selectedUser = userName
    }

    private fun exit() {
        exit = true
    }

    private fun message(text: String) {
        val currentUser = selectedUser
        if (currentUser == null) {
            println("User not selected. Use :user command")
            return
        }
        val address = users[currentUser]
        if (address == null) {
            println("Cannot send message, because user disappeared")
            return
        }
        val client = clients.getOrPut(currentUser) {
            when (address.protocol) {
                Protocol.WEBSOCKET -> WebSocketChatClient(address.host, address.port)
                Protocol.UDP -> UdpChatClient(address.host, address.port)
                else -> HttpChatClient(address.host, address.port)
            }
        }
        try {
            client.sendMessage(Message(name, text))
        } catch (e: Exception) {
            log.error("Error! ${e.message}", e)
        }
    }

    fun commandLoop() {
        var input: String
        printWelcome()
        updateUsersList()
        while (!exit) {
            input = prompt()
            when (input.substringBefore(" ")) {
                ":update" -> updateUsersList()
                ":exit" -> exit()
                ":user" -> {
                    val userName = input.split("""\s+""".toRegex()).drop(1).joinToString(" ")
                    selectUser(userName)
                }
                "" -> {
                }
                else -> thread {
                    message(input)
                }
            }
        }
    }

    private fun printWelcome() {
        println(
            """
                          ?????? ????                      
             _______     _       _       _   __   
            |__   __|   / \     | |     | | / /   
               | |     / ^ \    | |     | |/ /    
               | |    / /_\ \   | |     |    \     
               | |   / _____ \  | |___  | |\  \    
               |_|  /_/     \_\ |_____| |_| \__\ () () ()   
                                     
                    \ | /
                ^  -  O  -  
               / \^ / | \   
              /  / \        Hi, $name
             /  /   \     Welcome to Chat!
            """.trimIndent()
        )
    }

    override fun messageReceived(message: Message) {
        println("\nfrom [${message.user}] >>> ${message.text}")
    }
}