# Stage 1: Build React/Vite
FROM node:22-alpine AS build
WORKDIR /app
COPY package*.json ./
RUN npm install
COPY . .

# Add environment variable for API base URL 
ENV VITE_API_BASE_URL=/api/v1
RUN npm run build

# Stage 2: Setup Nginx
FROM nginx:alpine
# Copy Nginx configuration file into container
COPY nginx.conf /etc/nginx/conf.d/default.conf
# Copy built frontend files from the build stage to Nginx's html directory
COPY --from=build /app/dist /usr/share/nginx/html
EXPOSE 80
CMD ["nginx", "-g", "daemon off;"]