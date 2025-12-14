#!/bin/bash
# Script to monitor debug logs for ProfileScreen and Firebase

echo "=== Monitoring CoupleApp ProfileScreen Debug Logs ==="
echo "Waiting for logs... (Open app and navigate to Profile screen)"
echo ""

adb logcat -v time | grep -E "ProfileViewModel|ProfileScreen|FirebaseAuth|Firestore|CoupleApp" --line-buffered
