with open('app/src/main/res/values/strings.xml', 'r') as f:
    content = f.read()

content = content.replace('</resources>\n<string name="default_web_client_id">YOUR_CLIENT_ID</string>', '    <string name="default_web_client_id">YOUR_CLIENT_ID</string>\n</resources>')

with open('app/src/main/res/values/strings.xml', 'w') as f:
    f.write(content)
