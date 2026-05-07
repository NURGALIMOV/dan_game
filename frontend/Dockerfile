FROM nginx:1.27-alpine

# Replace default site config
RUN rm /etc/nginx/conf.d/default.conf
COPY nginx.conf  /etc/nginx/conf.d/default.conf

# Static assets
COPY index.html  /usr/share/nginx/html/index.html

EXPOSE 80
