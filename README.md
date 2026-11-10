# מעקב פרויקטים – גרסה 7 (טלפון + מחשב)

## עדכון ב-GitHub (ב-repository של האפליקציה)
1. Add file → Upload files: מעלים את כל הקבצים, Commit changes.
2. מחליפים את .github/workflows/main.yml בתוכן של build-apk.yml (פעם אחת, בגלל התוכנה למחשב).
3. אחרי וי ירוק: Releases → ProjectTracker.apk לטלפון, ProjectTracker-Desktop.exe למחשב.

## הגדרת סנכרון (פעם אחת)
1. ב-GitHub: New repository → שם: tracker-data → Private → Create.
2. Settings (של החשבון) → Developer settings → Personal access tokens → Fine-grained tokens → Generate new token:
   Repository access: Only select repositories → tracker-data.
   Permissions → Contents: Read and write. מעתיקים את הטוקן.
3. בטלפון: הגדרות → סנכרון בין מכשירים → מאגר: <שם-משתמש>/tracker-data, טוקן → שמירה וחיבור.
4. במחשב: אותו דבר.
