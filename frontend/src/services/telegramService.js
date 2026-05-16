const telegramService = {
    getBotUsername() {
        return import.meta.env.VITE_TELEGRAM_BOT || '';
    },

    buildTelegramDeepLink(userId) {
        if (!userId) {
            throw new Error('User ID is missing.');
        }
        const botUsername = this.getBotUsername();
        if (!botUsername) {
            throw new Error('Telegram bot username is not configured.');
        }
        return `https://t.me/${botUsername}?start=user_${userId}`;
    },

    openTelegramDeepLink(userId) {
        const link = this.buildTelegramDeepLink(userId);
        window.open(link, '_blank', 'noopener,noreferrer');
        return link;
    }
};

export default telegramService;
